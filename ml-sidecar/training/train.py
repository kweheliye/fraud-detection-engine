"""Train a model offline and save it as a versioned file.

    python -m training.train --version v1

Writes models/fraud-model-<version>.txt (the model) and .json (what it was trained on
and how well it did). The sidecar only ever LOADS these files, it never trains.
"""
import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import lightgbm as lgb
import numpy as np
from sklearn.metrics import average_precision_score, precision_recall_curve, roc_auc_score

from app.features import FEATURES
from app.model import model_paths
from training.data import START, generate


def _split_by_time(rows):
    """Train on the oldest 50% of days, validate on the next 20%, test on the newest 30%."""
    days = (max(r["timestamp"] for r in rows) - START).days + 1
    t_val, t_test = START + timedelta(days=days * 0.5), START + timedelta(days=days * 0.7)
    pick = lambda lo, hi: [r for r in rows if lo <= r["timestamp"] < hi]
    far = datetime.max.replace(tzinfo=timezone.utc)
    return pick(START, t_val), pick(t_val, t_test), pick(t_test, far)


def _xy(rows):
    return np.array([r["features"] for r in rows]), np.array([r["label"] for r in rows])


def _best_f1_threshold(y, scores) -> float:
    precision, recall, thresholds = precision_recall_curve(y, scores)
    f1 = 2 * precision[:-1] * recall[:-1] / np.maximum(precision[:-1] + recall[:-1], 1e-9)
    return float(thresholds[int(np.argmax(f1))])


def train(version: str, out_dir: Path, n_customers: int = 2000, days: int = 60, seed: int = 7) -> dict:
    rows = generate(n_customers=n_customers, days=days, seed=seed)
    train_rows, val_rows, test_rows = _split_by_time(rows)
    X_train, y_train = _xy(train_rows)
    X_val, y_val = _xy(val_rows)
    X_test, y_test = _xy(test_rows)

    clf = lgb.LGBMClassifier(
        n_estimators=500, learning_rate=0.05, num_leaves=31, min_child_samples=30,
        subsample=0.8, subsample_freq=1, colsample_bytree=0.8, random_state=seed, verbose=-1,
    )
    clf.fit(X_train, y_train, eval_X=X_val, eval_y=y_val, eval_metric="average_precision",
            callbacks=[lgb.early_stopping(30, verbose=False)])

    threshold = _best_f1_threshold(y_val, clf.predict_proba(X_val)[:, 1])   # chosen on validation, not test
    test_scores = clf.predict_proba(X_test)[:, 1]
    flagged = test_scores >= threshold
    tp = int(np.sum(flagged & (y_test == 1)))

    metadata = {
        "version": version,
        "trained_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "data": "synthetic (training/data.py)",
        "features": FEATURES,
        "rows": {"train": len(train_rows), "validation": len(val_rows), "test": len(test_rows)},
        "fraud_rate_test": round(float(y_test.mean()), 4),
        "metrics_on_test": {
            "pr_auc": round(float(average_precision_score(y_test, test_scores)), 4),
            "pr_auc_random_baseline": round(float(y_test.mean()), 4),
            "roc_auc": round(float(roc_auc_score(y_test, test_scores)), 4),
            "threshold": round(threshold, 4),
            "precision_at_threshold": round(tp / max(int(flagged.sum()), 1), 4),
            "recall_at_threshold": round(tp / max(int(y_test.sum()), 1), 4),
        },
        "lightgbm_version": lgb.__version__,
    }

    model_file, meta_file = model_paths(out_dir, version)
    out_dir.mkdir(parents=True, exist_ok=True)
    clf.booster_.save_model(str(model_file))
    meta_file.write_text(json.dumps(metadata, indent=2) + "\n")
    return metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", default="v1")
    parser.add_argument("--out", default="models")
    parser.add_argument("--customers", type=int, default=2000)
    parser.add_argument("--days", type=int, default=60)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    result = train(args.version, Path(args.out), args.customers, args.days, args.seed)
    print(json.dumps(result, indent=2))

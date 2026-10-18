import json

import pytest

from app.model import load_model, model_paths


def test_missing_model_fails_with_a_helpful_message(tmp_path):
    with pytest.raises(RuntimeError, match="python -m training.train"):
        load_model(tmp_path, "v9")


def test_model_trained_on_other_features_is_refused(tmp_path):
    from training.train import train
    train("v1", tmp_path, n_customers=150, days=30)
    _, meta_file = model_paths(tmp_path, "v1")
    meta = json.loads(meta_file.read_text())
    meta["features"] = meta["features"][:-1]
    meta_file.write_text(json.dumps(meta))
    with pytest.raises(RuntimeError, match="different features"):
        load_model(tmp_path, "v1")

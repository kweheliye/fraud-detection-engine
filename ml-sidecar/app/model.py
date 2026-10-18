"""Loads a versioned model file and scores rows with it."""
import json
from pathlib import Path

import lightgbm as lgb
import numpy as np

from app.features import FEATURES


class Model:
    def __init__(self, booster: lgb.Booster, metadata: dict):
        self.booster = booster
        self.metadata = metadata

    @property
    def version(self) -> str:
        return self.metadata["version"]

    def score(self, row: list[float]) -> float:
        """Probability (0..1) that the transaction is fraud."""
        return float(self.booster.predict(np.array([row]))[0])


def model_paths(model_dir: str | Path, version: str) -> tuple[Path, Path]:
    base = Path(model_dir) / f"fraud-model-{version}"
    return base.with_suffix(".txt"), base.with_suffix(".json")


def load_model(model_dir: str | Path, version: str) -> Model:
    model_file, meta_file = model_paths(model_dir, version)
    if not model_file.exists() or not meta_file.exists():
        raise RuntimeError(
            f"Model '{version}' not found in {model_dir} (expected {model_file.name} and {meta_file.name}). "
            "Train one with: python -m training.train"
        )
    metadata = json.loads(meta_file.read_text())
    if metadata["features"] != FEATURES:
        raise RuntimeError(
            f"Model '{version}' was trained on different features than this code builds. "
            "Retrain the model or use matching code."
        )
    # Native LightGBM text format: safe to load (unlike pickle, it cannot run code).
    return Model(lgb.Booster(model_file=str(model_file)), metadata)

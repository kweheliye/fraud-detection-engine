from app.model import load_model, model_paths
from training.train import train


def test_training_writes_a_loadable_versioned_model_that_beats_chance(tmp_path):
    metadata = train("v7", tmp_path, n_customers=400, days=40)

    model_file, meta_file = model_paths(tmp_path, "v7")
    assert model_file.exists() and meta_file.exists()

    metrics = metadata["metrics_on_test"]
    assert metrics["pr_auc"] > 5 * metrics["pr_auc_random_baseline"]

    assert load_model(tmp_path, "v7").version == "v7"

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:   # runs startup, which loads the model
        yield c


def payload(**overrides):
    body = dict(amount=45, customer_avg_amount=50, seconds_since_last_txn=21600, distance_from_last_km=0,
                txns_last_hour=0, is_new_device=False, timestamp="2026-03-10T20:00:00Z")
    body.update(overrides)
    return body


def test_health_reports_the_loaded_model(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"
    assert r.json()["model_version"] == "v1"


def test_model_endpoint_exposes_training_metrics(client):
    info = client.get("/model").json()
    assert info["version"] == "v1"
    assert info["metrics_on_test"]["pr_auc"] > 0.5


def test_normal_purchase_is_low_risk_at_every_hour_of_the_day(client):
    # Regression for the old prototype, whose model flagged normal evening purchases
    # because its training data was clustered around 13:00 UTC.
    for hour in range(24):
        r = client.post("/score", json=payload(timestamp=f"2026-03-10T{hour:02d}:00:00Z"))
        assert r.json()["score"] < 0.1, f"normal purchase scored high at {hour}:00"


def test_suspicious_purchase_is_high_risk(client):
    r = client.post("/score", json=payload(amount=1200, seconds_since_last_txn=40, distance_from_last_km=6000,
                                           txns_last_hour=3, is_new_device=True, timestamp="2026-03-10T03:00:00Z"))
    assert r.json()["score"] > 0.7


def test_first_ever_transaction_without_history_is_scored(client):
    r = client.post("/score", json=payload(customer_avg_amount=None, seconds_since_last_txn=None,
                                           distance_from_last_km=None, is_new_device=True))
    assert r.status_code == 200
    assert 0 <= r.json()["score"] <= 1


def test_response_names_the_model_version(client):
    assert client.post("/score", json=payload()).json()["model_version"] == "v1"


@pytest.mark.parametrize("bad", [
    {"amount": -5},
    {"amount": 0},
    {"txns_last_hour": -1},
    {"timestamp": "yesterday"},
])
def test_invalid_input_is_rejected(client, bad):
    assert client.post("/score", json=payload(**bad)).status_code == 422


def test_missing_required_field_is_rejected(client):
    body = payload()
    del body["is_new_device"]
    assert client.post("/score", json=body).status_code == 422

# ML sidecar

A small Python service that gives the engine a second opinion: it looks at how a transaction
compares with the customer's own history and returns a fraud probability between 0 and 1.
The Java engine calls it, and the `ML_SCORING` rule turns the score into rule points.

- **Model:** LightGBM, trained offline. The service only *loads* a versioned model file, it never trains at startup.
- **Evaluation:** PR-AUC on a time-based split (train on the past, test on the future). PR-AUC is used because
  fraud is rare, so plain accuracy would look great even for a model that flags nothing.
- **Why not pickle:** models are saved in LightGBM's own text format, which cannot execute code when loaded.

## Run it

```bash
make ml            # build and start on http://localhost:8000 (needs Docker)
```

or without Docker:

```bash
cd ml-sidecar
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --port 8000
```

## API

`POST /score`

```json
{
  "amount": 20000,
  "customer_avg_amount": 55.0,
  "seconds_since_last_txn": 30,
  "distance_from_last_km": 6500,
  "txns_last_hour": 2,
  "is_new_device": true,
  "timestamp": "2026-10-18T03:10:00Z"
}
```

```json
{ "score": 0.9598, "model_version": "v1" }
```

| Field | Meaning |
|---|---|
| `amount` | This transaction's amount (> 0) |
| `customer_avg_amount` | Average of the customer's earlier transactions. `null` for a first transaction |
| `seconds_since_last_txn` | Time since the customer's previous transaction. `null` for a first transaction |
| `distance_from_last_km` | Distance from the previous transaction's location. `null` if unknown |
| `txns_last_hour` | Earlier transactions in the last hour, **not** counting this one |
| `is_new_device` | `true` if the device has not been seen for this customer before |
| `timestamp` | ISO-8601. A missing time zone is read as UTC |

Missing history is sent as `null`; the model handles it natively, so the caller never invents placeholder values.

`GET /health` returns the loaded model version. `GET /model` returns the model's training details and metrics.
Invalid input returns `422`.

## Models and versions

Model files live in `models/` as a pair: `fraud-model-<version>.txt` (the model) and `.json`
(features, data sizes, metrics, suggested threshold). The service picks the version with `MODEL_VERSION`
(default `v1`) and refuses to start if the files are missing or were trained on different features.

```bash
make ml-train VERSION=v2                      # train a new version
MODEL_VERSION=v2 .venv/bin/uvicorn app.main:app   # serve it
```

## The training data is synthetic

`training/data.py` generates demo transactions so the project works out of the box. It simulates customers
with their own spending, devices, home city and daily rhythm (spread over all 24 hours), legitimate behaviour
that looks odd (trips, big purchases, a new phone), and several kinds of fraud, some of them hard to spot.

**The metrics in `models/fraud-model-v1.json` describe how well the model learned this simulation, not how it
would do on real fraud.** For production, train on your own labelled history: build rows with
`app.features.build_row()` (the same function the service uses, so training and serving cannot drift apart),
replace the generator in `training/train.py`, and compare PR-AUC against the random baseline stored beside it.

## Tests

```bash
make ml-test
```

They include a regression test that scores the same normal purchase at every hour of the day and requires it
to stay low risk, because an earlier prototype flagged normal evening purchases after being trained mostly on
13:00 UTC data.

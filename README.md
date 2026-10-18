# Fraud Detection Engine

Open-source, real-time transaction fraud detection built on **Spring Boot**, **Kafka Streams** and **jOOQ**,
with an optional **Python ML sidecar**.

Transactions arrive on a Kafka topic, are scored by stateful rules (and optionally an ML model),
and are routed to `scored` and `flagged` topics. Results are persisted to PostgreSQL and can be read over REST.


## Features

- **Stateful rules inside the stream:** velocity, geo mismatch and new-device state live in a Kafka Streams
  state store, partitioned by customer. No Redis and no external cache to keep in sync (see [Design decisions](#design-decisions)).
- **Exactly-once processing** (`exactly_once_v2`): state updates, output records and offsets commit atomically,
  so a retry never double-counts a transaction.
- **Idempotent persistence:** `INSERT ... ON CONFLICT DO NOTHING` on `transaction_id`, so redelivered
  messages cannot create duplicate rows.
- **Type-safe SQL** with jOOQ, generated from the Flyway migrations at build time. No ORM.
- **Configurable, independently testable rules:** every rule is its own class, and its weight, threshold and
  on/off switch live in `application.yaml`.
- **Optional ML scoring** via a Python sidecar (FastAPI + LightGBM) using behavioral features. If the sidecar
  is down or slow, the ML rule adds 0 points and the other rules still score the transaction.
- **REST API** to submit transactions and read back scored and flagged results.

## Architecture

```
POST /api/transactions
        |
        v
  transactions.raw --> Kafka Streams (stateful rules + ML score) <--HTTP--> ML sidecar (/score)
                              |
              +---------------+----------------+
              v                                v
   transactions.scored (all)       transactions.flagged (HIGH only)
              |                                |
              v                                v
        Postgres                         Postgres
              |
              v
   GET /api/transactions, GET /api/flagged
```

Messages are keyed by `customerId`, so all of one customer's events (and their rule state) are handled
by the same stream task.

## Risk scoring

Each triggered rule adds its weight. The total is capped at 100.

| Rule | Weight | Fires when |
|---|---|---|
| `AMOUNT_THRESHOLD` | 40 | amount is above the configured limit (default 10,000) |
| `GEO_MISMATCH` | 25 | the customer's country changes within one hour |
| `VELOCITY` | 25 | more than N transactions in a time window (default 5 per 60 s) |
| `NEW_DEVICE` | 10 | the device has not been seen for this customer |
| `ML_SCORING` | 35 | the ML sidecar's score is at or above its threshold (default 0.5). Off by default |

| Score | Risk level | Outcome |
|---|---|---|
| 0-29 | LOW | approved |
| 30-69 | MEDIUM | review |
| 70-100 | HIGH | blocked, published to `transactions.flagged` |

Weights, thresholds, windows and the on/off switch for each rule are in `src/main/resources/application.yaml`
under `fraud.rules`. The LOW/MEDIUM/HIGH cut-offs are `fraud.medium-from` and `fraud.high-from`.

## ML sidecar

A small FastAPI service in [`ml-sidecar/`](ml-sidecar/README.md) that scores a transaction with a LightGBM model.

- **Behavioral features:** amount vs the customer's average, time since the last transaction, distance from the
  last location, country change, transactions in the last hour, new device, and time of day.
- **Versioned model file**, loaded once at startup (`MODEL_VERSION`). The model is never trained at startup.
- **Evaluated with PR-AUC** on a time-based split. Metrics are saved next to the model file.
- **Synthetic training data.** The reported metrics describe the simulation, not real-world fraud.
- Includes a regression test that a normal purchase stays low at every hour of the day.

The Spring Boot app calls `POST /score` with a 500 ms timeout. After a failed call it skips the sidecar for 10 seconds,
so a dead sidecar does not slow every transaction.

## Tech stack

| Area | Choice |
|---|---|
| Language / build | Java 25, Maven |
| Framework | Spring Boot 4.1 |
| Streaming | Kafka Streams (state stores, `exactly_once_v2`) |
| Database | PostgreSQL 16, Flyway migrations, jOOQ |
| ML sidecar | Python, FastAPI, LightGBM |
| Local infra | Docker Compose (Kafka in KRaft mode, Postgres, ML sidecar, optional Kafka UI) |
| Testing | JUnit, `TopologyTestDriver`, pytest |

## Design decisions

### Why Kafka Streams and not Apache Flink?

Flink is an excellent stream processor, and this project does not claim otherwise. Kafka Streams was chosen
because it fits this problem and keeps the project easy to run and contribute to:

- **It is a library, not a cluster.** Kafka Streams runs inside the Spring Boot application. Flink needs a
  separate JobManager and TaskManagers to deploy, monitor and upgrade. Here, `make run` starts one app, and
  scaling out means starting another instance.
- **Kafka is already the backbone.** Input, output and state backups (changelog topics) all use Kafka, so there
  is no second system to learn or operate.
- **The workload is per-customer state.** Velocity, geo and device checks are keyed by `customerId`. That is
  the case Kafka Streams state stores handle well, partitioned by key.
- **One language and toolchain.** Everything is Java and Spring Boot, with `TopologyTestDriver` for fast
  topology tests that need no cluster.

**Trade-offs we accept.** Flink is stronger for complex event processing (pattern matching across event
sequences), very large state, many sources and sinks beyond Kafka, SQL-based jobs, and savepoints for
upgrades. If the project outgrows Kafka Streams, for example with multi-source joins or patterns Kafka
Streams cannot express cleanly, Flink is the natural next step. Each rule is a small class in
`domain.rules` that takes a transaction and a customer profile, so the scoring logic can move to another
engine without being rewritten.

### Why no Redis?

Fraud rules need memory: velocity counters, the last-seen location and the set of known devices per customer.
A common approach is to keep that state in an external store such as Redis. We keep it in Kafka Streams state
stores instead, for these reasons:

- **Exactly-once.** A Redis write inside a rule is not part of the Kafka transaction. If a record is
  retried, the write happens twice: the velocity count goes up again and a device is marked "seen" before the
  result is committed. State stores are committed atomically with the output records and offsets
  (`exactly_once_v2`).
- **Replayable.** Reprocessing a topic rebuilds the state exactly, because state is derived from the
  stream. Redis would keep the old values and give different results on replay.
- **No network hop per rule.** State is local to the stream task (RocksDB), instead of several Redis calls
  for every transaction.
- **One less service.** Fewer containers to run, secure and keep consistent.

**Trade-offs we accept.** State stores are private to the application, so other services cannot read a
customer's profile directly (Kafka Streams interactive queries or a published topic can expose it). After a
rebalance or restart, a task restores its state from the changelog topic, which takes time on large state.
Redis would make sense again if other services need low-latency lookups of this data outside the stream.

## Getting started

**Requirements:** JDK 25 and Docker (running).

```bash
make run       # start Kafka, Postgres, the ML sidecar and the app (ML rule on) on http://localhost:8080
make stop      # stop everything
```

| Command | What it does |
|---|---|
| **Everything** | |
| `make run` | Start Kafka, Postgres and the ML sidecar, then run the app with the ML rule on. `Ctrl+C` stops the app |
| `make stop` | Stop Kafka, Postgres, the ML sidecar and Kafka UI (your data is kept) |
| **Spring Boot engine only (no ML)** | |
| `make engine` | Start Kafka and Postgres, then run the app with the ML rule off |
| `make engine-stop` | Stop Kafka and Postgres |
| **ML sidecar only** | |
| `make ml` | Build and start the sidecar on http://localhost:8000 |
| `make ml-stop` | Stop the sidecar |
| `make ml-test` | Run the sidecar tests (needs Python 3.10+) |
| `make ml-train VERSION=v2` | Train a new model version |
| **Build** | |
| `make test` | Run the Java tests |
| `make build` | Build the jar into `target/` |
| `make clean` | Delete build output |

To wipe the database as well: `docker compose --profile ui --profile ml down -v`.

Check that it is up: `curl http://localhost:8080/actuator/health` and `curl http://localhost:8000/health`.

Default local ports: Kafka `9092`, Postgres `5432` (database, user and password are all `fraud`), app `8080`,
ML sidecar `8000`. To browse topics, run `docker compose --profile ui up -d kafka-ui` and open http://localhost:8085.

## API

```bash
# Submit a transaction (202 Accepted). latitude and longitude are optional.
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-1",
    "customerId": "cust-1",
    "merchantId": "merch-amazon",
    "amount": 45,
    "country": "US",
    "deviceId": "dev-iphone-1",
    "timestamp": "2026-10-18T13:00:00Z",
    "latitude": 40.7,
    "longitude": -74.0
  }'
```

| Method and path | Returns |
|---|---|
| `POST /api/transactions` | `202 Accepted` after the transaction is sent to Kafka. Invalid input gets `400` with the failing fields |
| `GET /api/transactions/{id}` | One scored transaction, or `404` |
| `GET /api/transactions?customerId=cust-1&limit=50` | Recent scored transactions (limit 1 to 200) |
| `GET /api/flagged` | Recent HIGH-risk transactions |

Results appear shortly after submitting, because scoring happens asynchronously in the stream.
Live Server-Sent Events feeds (`/api/stream/transactions`) are planned.

## Project layout

```
src/main/java/io/github/kweheliye/frauddetection/
  api/           REST controller and request/response DTOs (api/dto)
  service/       submit and query services
  domain/        records (Transaction, CustomerProfile, Decision, ...) and rules (domain/rules)
  streams/       Kafka Streams topology, score processor and topic creation
  ml/            HTTP client for the ML sidecar
  persistence/   jOOQ repository and the Kafka listeners that save results
  exception/     global exception handler and custom exceptions
src/main/resources/application.yaml   all settings: Kafka, topics, rules, thresholds, ML
src/main/resources/db/migration/      Flyway migrations (single source of truth for the schema)
ml-sidecar/      Python ML service: app/, training/, models/, tests/
```

jOOQ classes are generated from the migrations into `target/generated-sources/jooq`, so no running
database is needed to compile.

## Roadmap

- [x] Project skeleton, Docker Compose, first database migration
- [x] Rules with unit tests, configured from `application.yaml`
- [x] Kafka Streams topology with a state store
- [x] jOOQ repositories and consumers (scored and flagged)
- [x] REST API with validation
- [x] ML sidecar with behavioral features and a versioned model
- [ ] GitHub Actions CI (Java and sidecar tests)
- [ ] Live SSE feeds
- [ ] Testcontainers integration tests
- [ ] Dashboard (optional)

## Contributing

Issues and pull requests are welcome. Please add tests with every rule change.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

# Fraud Detection Engine

Open-source, real-time transaction fraud detection built on **Spring Boot**, **Kafka Streams** and **jOOQ**.

Transactions arrive on a Kafka topic, are scored by stateful rules (and optionally an ML model),
and are routed to `scored` and `flagged` topics. Results are persisted to PostgreSQL.

> **Status: early development.** This is a ground-up rebuild of an earlier prototype. Rule state moves
> from Redis into Kafka Streams state stores with exactly-once processing, and persistence moves from
> JPA to jOOQ. Sections marked *(planned)* are not implemented yet.

## Features

- **Stateful rules inside the stream:** velocity, geo mismatch and new-device state live in Kafka Streams
  state stores, partitioned by customer. No Redis and no external cache to keep in sync (see [Design decisions](#design-decisions)).
- **Exactly-once processing** (`exactly_once_v2`): state updates, output records and offsets commit atomically,
  so a retry never double-counts a transaction. *(planned: verified by tests)*
- **Idempotent persistence:** `INSERT ... ON CONFLICT DO NOTHING` on `transaction_id`, so redelivered
  messages cannot create duplicate rows.
- **Type-safe SQL** with jOOQ, generated from the Flyway migrations at build time (no JPA).
- **Pure, testable rules:** scoring logic is plain Java with no Spring or Kafka imports. *(planned)*
- **Optional ML scoring** via a Python sidecar (FastAPI, scikit-learn, LightGBM). *(planned)*

## Architecture

```
POST /api/transactions
        |
        v
  transactions.raw --> Kafka Streams (stateful rules + ML score)
                              |
              +---------------+----------------+
              v                                v
   transactions.scored (all)       transactions.flagged (HIGH only)
              |                                |
              v                                v
   Postgres + live feed (SSE)      Postgres + alert webhook
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
| `ML_SCORING` | 35 | the ML sidecar's risk score is at or above its threshold |

| Score | Risk level | Outcome |
|---|---|---|
| 0-29 | LOW | approved |
| 30-69 | MEDIUM | review |
| 70-100 | HIGH | blocked, published to `transactions.flagged` |

Weights and thresholds are starting values and will become configuration properties.

## Tech stack

| Area | Choice |
|---|---|
| Language / build | Java 25, Maven |
| Framework | Spring Boot 4.1 |
| Streaming | Kafka Streams (state stores, `exactly_once_v2`) |
| Database | PostgreSQL 16, Flyway migrations, jOOQ |
| Local infra | Docker Compose (Kafka in KRaft mode, Postgres, optional Kafka UI) |
| Testing | JUnit, `TopologyTestDriver`, Testcontainers, ArchUnit |

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
Streams cannot express cleanly, Flink is the natural next step. The rules are plain Java in the `domain`
package, so they can move to another engine without being rewritten.

### Why no Redis?

The original prototype kept rule state (velocity counters, last-seen location, known devices) in Redis.
That worked, but it had real problems, so state now lives in Kafka Streams state stores:

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
make run       # start Kafka + Postgres, then the app on http://localhost:8080
make stop      # shut down Kafka + Postgres
```

| Command | What it does |
|---|---|
| `make run` | Start Kafka and Postgres, wait until they are ready, then run the app. `Ctrl+C` stops the app |
| `make stop` | Stop Kafka and Postgres (your data is kept) |
| `make test` | Run the tests |
| `make build` | Build the jar into `target/` |
| `make clean` | Delete build output |

To wipe the database as well: `docker compose down -v`.

Check that it is up: `curl http://localhost:8080/actuator/health`

Default local ports: Kafka `9092`, Postgres `5432` (database, user and password are all `fraud`), app `8080`.
To browse topics, run `docker compose --profile ui up -d kafka-ui` and open http://localhost:8085.

## API *(planned)*

```bash
# Submit a transaction (202 Accepted)
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-1",
    "customerId": "cust-1",
    "merchantId": "merch-amazon",
    "amount": 45,
    "country": "US",
    "deviceId": "dev-iphone-1",
    "timestamp": "2026-10-18T13:00:00Z"
  }'
```

Live results will be available as Server-Sent Events at `/api/stream/transactions` and
`/api/stream/alerts`.

## Project layout

```
src/main/java/io/github/kweheliye/frauddetection/
  domain/        pure Java: records and rules (no Spring, Kafka or jOOQ)   (planned)
  streams/       Kafka Streams topology, processors and serdes             (planned)
  ml/            ML scorer interface and HTTP client                       (planned)
  persistence/   jOOQ repositories                                         (planned)
  api/           REST controllers and DTOs                                 (planned)
  alerting/      webhook alerts                                            (planned)
  config/        typed configuration properties                            (planned)
src/main/resources/db/migration/   Flyway migrations (single source of truth for the schema)
```

jOOQ classes are generated from the migrations into `target/generated-sources/jooq`, so no running
database is needed to compile.

## Roadmap

- [x] Project skeleton, Docker Compose, first database migration
- [ ] Domain model and pure rules with unit tests
- [ ] Kafka Streams topology with state stores
- [ ] jOOQ repositories and consumers (scored and flagged)
- [ ] REST API and live SSE feeds
- [ ] ML sidecar with behavioral features and a versioned model
- [ ] Testcontainers integration tests and GitHub Actions CI
- [ ] Dashboard (optional)

## Contributing

Issues and pull requests are welcome. Please keep `domain` free of framework imports (an ArchUnit test
will enforce this) and add tests with every rule change.

## License

Licensed under the Apache License, Version 2.0. Add the full text with:

```bash
curl -fsSL -o LICENSE https://www.apache.org/licenses/LICENSE-2.0.txt
```

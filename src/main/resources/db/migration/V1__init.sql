-- Every transaction that has been scored. transaction_id is the primary key so that
-- a redelivered Kafka message can never create a duplicate row
-- (writes use INSERT ... ON CONFLICT (transaction_id) DO NOTHING).
CREATE TABLE transaction_events (
    transaction_id  VARCHAR(64)      NOT NULL,
    customer_id     VARCHAR(64)      NOT NULL,
    merchant_id     VARCHAR(64)      NOT NULL,
    amount          NUMERIC(18, 2)   NOT NULL,
    country         VARCHAR(8)       NOT NULL,
    city            VARCHAR(128),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    device_id       VARCHAR(128)     NOT NULL,
    ip_address      VARCHAR(64),
    event_time      TIMESTAMP WITH TIME ZONE NOT NULL,
    score           INTEGER          NOT NULL,
    risk_level      VARCHAR(8)       NOT NULL,
    triggered_rules TEXT[]           NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transaction_events PRIMARY KEY (transaction_id),
    CONSTRAINT ck_transaction_events_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_transaction_events_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX ix_transaction_events_customer_time ON transaction_events (customer_id, event_time DESC);
CREATE INDEX ix_transaction_events_risk_time ON transaction_events (risk_level, event_time DESC);

-- HIGH-risk transactions that raised an alert. No foreign key to transaction_events on
-- purpose: the scored and flagged topics are consumed independently, so either row can
-- arrive first.
CREATE TABLE flagged_transactions (
    transaction_id  VARCHAR(64)      NOT NULL,
    customer_id     VARCHAR(64)      NOT NULL,
    amount          NUMERIC(18, 2)   NOT NULL,
    score           INTEGER          NOT NULL,
    triggered_rules TEXT[]           NOT NULL,
    flagged_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    alert_sent_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_flagged_transactions PRIMARY KEY (transaction_id)
);

CREATE INDEX ix_flagged_transactions_customer ON flagged_transactions (customer_id, flagged_at DESC);

package io.github.kweheliye.frauddetection.persistence;

import static io.github.kweheliye.frauddetection.persistence.jooq.Tables.FLAGGED_TRANSACTIONS;
import static io.github.kweheliye.frauddetection.persistence.jooq.Tables.TRANSACTION_EVENTS;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import io.github.kweheliye.frauddetection.domain.ScoredTransaction;
import io.github.kweheliye.frauddetection.domain.FlaggedTransaction;
import io.github.kweheliye.frauddetection.domain.StoredTransaction;
import io.github.kweheliye.frauddetection.domain.Transaction;
import io.github.kweheliye.frauddetection.persistence.jooq.tables.records.FlaggedTransactionsRecord;
import io.github.kweheliye.frauddetection.persistence.jooq.tables.records.TransactionEventsRecord;

/**
 * Writes scored transactions to Postgres. Every insert is idempotent:
 * if Kafka delivers the same message twice, the second write is silently ignored.
 */
@Repository
public class TransactionRepository {

    private final DSLContext db;

    public TransactionRepository(DSLContext db) {
        this.db = db;
    }

    public void saveScored(ScoredTransaction scored) {
        Transaction txn = scored.transaction();
        db.insertInto(TRANSACTION_EVENTS)
                .set(TRANSACTION_EVENTS.TRANSACTION_ID, txn.transactionId())
                .set(TRANSACTION_EVENTS.CUSTOMER_ID, txn.customerId())
                .set(TRANSACTION_EVENTS.MERCHANT_ID, txn.merchantId())
                .set(TRANSACTION_EVENTS.AMOUNT, txn.amount())
                .set(TRANSACTION_EVENTS.COUNTRY, txn.country())
                .set(TRANSACTION_EVENTS.DEVICE_ID, txn.deviceId())
                .set(TRANSACTION_EVENTS.LATITUDE, txn.latitude())
                .set(TRANSACTION_EVENTS.LONGITUDE, txn.longitude())
                .set(TRANSACTION_EVENTS.EVENT_TIME, txn.timestamp().atOffset(ZoneOffset.UTC))
                .set(TRANSACTION_EVENTS.SCORE, scored.decision().score())
                .set(TRANSACTION_EVENTS.RISK_LEVEL, scored.decision().riskLevel().name())
                .set(TRANSACTION_EVENTS.TRIGGERED_RULES, scored.decision().triggeredRules().toArray(String[]::new))
                .onConflictDoNothing()
                .execute();
    }

    public void saveFlagged(ScoredTransaction scored) {
        Transaction txn = scored.transaction();
        db.insertInto(FLAGGED_TRANSACTIONS)
                .set(FLAGGED_TRANSACTIONS.TRANSACTION_ID, txn.transactionId())
                .set(FLAGGED_TRANSACTIONS.CUSTOMER_ID, txn.customerId())
                .set(FLAGGED_TRANSACTIONS.AMOUNT, txn.amount())
                .set(FLAGGED_TRANSACTIONS.SCORE, scored.decision().score())
                .set(FLAGGED_TRANSACTIONS.TRIGGERED_RULES, scored.decision().triggeredRules().toArray(String[]::new))
                .onConflictDoNothing()
                .execute();
    }

    public Optional<StoredTransaction> findById(String transactionId) {
        return db.selectFrom(TRANSACTION_EVENTS)
                .where(TRANSACTION_EVENTS.TRANSACTION_ID.eq(transactionId))
                .fetchOptional(TransactionRepository::toStored);
    }

    /** Newest first. Pass a null customerId to get everyone's. */
    public List<StoredTransaction> findRecent(String customerId, int limit) {
        Condition filter = customerId == null ? DSL.noCondition() : TRANSACTION_EVENTS.CUSTOMER_ID.eq(customerId);
        return db.selectFrom(TRANSACTION_EVENTS)
                .where(filter)
                .orderBy(TRANSACTION_EVENTS.EVENT_TIME.desc())
                .limit(limit)
                .fetch(TransactionRepository::toStored);
    }

    public List<FlaggedTransaction> findRecentFlagged(int limit) {
        return db.selectFrom(FLAGGED_TRANSACTIONS)
                .orderBy(FLAGGED_TRANSACTIONS.FLAGGED_AT.desc())
                .limit(limit)
                .fetch(TransactionRepository::toFlagged);
    }

    private static StoredTransaction toStored(TransactionEventsRecord r) {
        return new StoredTransaction(r.getTransactionId(), r.getCustomerId(), r.getMerchantId(), r.getAmount(),
                r.getCountry(), r.getDeviceId(), r.getEventTime().toInstant(), r.getScore(), r.getRiskLevel(),
                List.of(r.getTriggeredRules()));
    }

    private static FlaggedTransaction toFlagged(FlaggedTransactionsRecord r) {
        return new FlaggedTransaction(r.getTransactionId(), r.getCustomerId(), r.getAmount(), r.getScore(),
                List.of(r.getTriggeredRules()), r.getFlaggedAt().toInstant());
    }
}

package io.github.kweheliye.frauddetection.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A HIGH-risk transaction that raised an alert. */
public record FlaggedTransaction(
        String transactionId,
        String customerId,
        BigDecimal amount,
        int score,
        List<String> triggeredRules,
        Instant flaggedAt) {
}

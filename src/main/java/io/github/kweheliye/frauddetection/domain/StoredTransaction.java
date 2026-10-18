package io.github.kweheliye.frauddetection.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A scored transaction as it was saved. */
public record StoredTransaction(
        String transactionId,
        String customerId,
        String merchantId,
        BigDecimal amount,
        String country,
        String deviceId,
        Instant eventTime,
        int score,
        String riskLevel,
        List<String> triggeredRules) {
}

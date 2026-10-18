package io.github.kweheliye.frauddetection.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.github.kweheliye.frauddetection.domain.StoredTransaction;

/** A scored transaction as the API returns it. */
public record TransactionResponse(
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

    public static TransactionResponse from(StoredTransaction t) {
        return new TransactionResponse(t.transactionId(), t.customerId(), t.merchantId(), t.amount(),
                t.country(), t.deviceId(), t.eventTime(), t.score(), t.riskLevel(), t.triggeredRules());
    }
}

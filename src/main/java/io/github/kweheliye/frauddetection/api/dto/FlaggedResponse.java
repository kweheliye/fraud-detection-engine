package io.github.kweheliye.frauddetection.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.github.kweheliye.frauddetection.domain.FlaggedTransaction;

/** A flagged (HIGH-risk) transaction as the API returns it. */
public record FlaggedResponse(
        String transactionId,
        String customerId,
        BigDecimal amount,
        int score,
        List<String> triggeredRules,
        Instant flaggedAt) {

    public static FlaggedResponse from(FlaggedTransaction f) {
        return new FlaggedResponse(f.transactionId(), f.customerId(), f.amount(), f.score(),
                f.triggeredRules(), f.flaggedAt());
    }
}

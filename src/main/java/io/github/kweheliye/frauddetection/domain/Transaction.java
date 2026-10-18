package io.github.kweheliye.frauddetection.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(
        String transactionId,
        String customerId,
        String merchantId,
        BigDecimal amount,
        String country,
        String deviceId,
        Instant timestamp) {
}

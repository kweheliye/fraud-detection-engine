package io.github.kweheliye.frauddetection.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** latitude and longitude are optional: many payment channels do not report a location. */
public record Transaction(
        String transactionId,
        String customerId,
        String merchantId,
        BigDecimal amount,
        String country,
        String deviceId,
        Instant timestamp,
        Double latitude,
        Double longitude) {
}

package io.github.kweheliye.frauddetection.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.github.kweheliye.frauddetection.domain.Transaction;

/** What the API accepts. The size limits match the database columns. */
public record TransactionRequest(
        @NotBlank @Size(max = 64) String transactionId,
        @NotBlank @Size(max = 64) String customerId,
        @NotBlank @Size(max = 64) String merchantId,
        @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "must be a 2-letter country code") String country,
        @NotBlank @Size(max = 128) String deviceId,
        Instant timestamp,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {

    /**
     * timestamp is optional: when missing, the transaction is stamped with the current time.
     * latitude and longitude are optional too (used to measure distance between purchases).
     */
    public Transaction toTransaction() {
        return new Transaction(transactionId, customerId, merchantId, amount, country, deviceId,
                timestamp != null ? timestamp : Instant.now(), latitude, longitude);
    }
}

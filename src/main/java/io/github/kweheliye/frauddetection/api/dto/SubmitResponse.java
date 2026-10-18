package io.github.kweheliye.frauddetection.api.dto;

/** Returned when a transaction has been accepted for scoring. */
public record SubmitResponse(String transactionId, String status) {

    public static SubmitResponse accepted(String transactionId) {
        return new SubmitResponse(transactionId, "ACCEPTED");
    }
}

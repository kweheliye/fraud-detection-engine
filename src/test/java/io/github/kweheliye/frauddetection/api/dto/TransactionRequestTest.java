package io.github.kweheliye.frauddetection.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class TransactionRequestTest {

    private final Validator validator = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory().getValidator();

    private TransactionRequest valid() {
        return new TransactionRequest("t1", "c1", "shop", new BigDecimal("50.00"), "AE", "d1", null, null, null);
    }

    private String fieldsOf(TransactionRequest r) {
        Set<ConstraintViolation<TransactionRequest>> v = validator.validate(r);
        return v.stream().map(x -> x.getPropertyPath().toString()).sorted().toList().toString();
    }

    @Test
    void validRequestPasses() {
        assertThat(validator.validate(valid()).isEmpty()).isTrue();
    }

    @Test
    void missingTimestampDefaultsToNow() {
        assertThat(valid().toTransaction().timestamp() != null).isTrue();
        Instant given = Instant.parse("2026-01-01T10:00:00Z");
        TransactionRequest r = new TransactionRequest("t1", "c1", "shop", BigDecimal.ONE, "AE", "d1", given, null, null);
        assertThat(r.toTransaction().timestamp()).isEqualTo(given);
    }

    @Test
    void blankIdsAreRejected() {
        assertThat(fieldsOf(new TransactionRequest(" ", null, "", BigDecimal.TEN, "AE", "d1", null, null, null)))
                .isEqualTo("[customerId, merchantId, transactionId]");
    }

    @Test
    void amountMustBePositiveAndFitTheColumn() {
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", new BigDecimal("-5"), "AE", "d", null, null, null))).isEqualTo("[amount]");
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", BigDecimal.ZERO, "AE", "d", null, null, null))).isEqualTo("[amount]");
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", null, "AE", "d", null, null, null))).isEqualTo("[amount]");
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", new BigDecimal("1.234"), "AE", "d", null, null, null))).isEqualTo("[amount]");
    }

    @Test
    void countryMustBeTwoLetters() {
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", BigDecimal.TEN, "USA", "d", null, null, null))).isEqualTo("[country]");
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", BigDecimal.TEN, "A1", "d", null, null, null))).isEqualTo("[country]");
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", BigDecimal.TEN, "us", "d", null, null, null))).isEqualTo("[]");
    }

    @Test
    void tooLongIdsAreRejected() {
        String longId = "x".repeat(65);
        assertThat(fieldsOf(new TransactionRequest(longId, "c", "m", BigDecimal.TEN, "AE", "d", null, null, null))).isEqualTo("[transactionId]");
    }

    @Test
    void coordinatesAreOptionalButMustBeRealPlaces() {
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", BigDecimal.TEN, "AE", "d", null, 25.2, 55.3))).isEqualTo("[]");
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", BigDecimal.TEN, "AE", "d", null, 91.0, 55.3))).isEqualTo("[latitude]");
        assertThat(fieldsOf(new TransactionRequest("t", "c", "m", BigDecimal.TEN, "AE", "d", null, 25.2, -181.0))).isEqualTo("[longitude]");
    }
}

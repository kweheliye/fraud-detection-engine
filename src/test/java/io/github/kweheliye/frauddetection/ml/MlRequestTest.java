package io.github.kweheliye.frauddetection.ml;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;

class MlRequestTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration KEEP = Duration.ofHours(1);

    private Transaction txn(String amount, long seconds, String country, String device, Double lat, Double lon) {
        return new Transaction("t", "c1", "shop", new BigDecimal(amount), country, device, T0.plusSeconds(seconds), lat, lon);
    }

    @Test
    void aFirstTransactionHasNoHistoryAndIsOnANewDevice() {
        MlRequest r = MlRequest.of(txn("45", 0, "AE", "d1", null, null), CustomerProfile.empty());

        assertThat(r.amount()).isEqualTo(45.0);
        assertThat(r.customerAvgAmount()).isNull();
        assertThat(r.secondsSinceLastTxn()).isNull();
        assertThat(r.distanceFromLastKm()).isNull();
        assertThat(r.countryChanged()).isNull();
        assertThat(r.txnsLastHour()).isZero();
        assertThat(r.newDevice()).isTrue();
    }

    @Test
    void comparesTheTransactionWithTheCustomersHistory() {
        CustomerProfile profile = CustomerProfile.empty()
                .with(txn("40", 0, "AE", "d1", 25.20, 55.27), KEEP)
                .with(txn("60", 600, "AE", "d1", 25.20, 55.27), KEEP);

        MlRequest r = MlRequest.of(txn("500", 700, "GB", "d9", 51.51, -0.13), profile);

        assertThat(r.customerAvgAmount()).isEqualTo(50.0);
        assertThat(r.secondsSinceLastTxn()).isEqualTo(100.0);
        assertThat(r.countryChanged()).isTrue();
        assertThat(r.newDevice()).isTrue();
        assertThat(r.txnsLastHour()).isEqualTo(2);
        assertThat(r.distanceFromLastKm()).isBetween(5400.0, 5600.0);   // Dubai to London is about 5,500 km
    }

    @Test
    void sameCountryAndKnownDeviceAreNotSuspicious() {
        CustomerProfile profile = CustomerProfile.empty().with(txn("40", 0, "AE", "d1", null, null), KEEP);
        MlRequest r = MlRequest.of(txn("40", 30, "ae", "d1", null, null), profile);

        assertThat(r.countryChanged()).isFalse();
        assertThat(r.newDevice()).isFalse();
        assertThat(r.distanceFromLastKm()).isNull();   // no coordinates: unknown, not zero
    }

    @Test
    void onlyCountsTransactionsFromTheLastHour() {
        CustomerProfile profile = CustomerProfile.empty()
                .with(txn("5", 0, "AE", "d1", null, null), Duration.ofHours(5))
                .with(txn("5", 4000, "AE", "d1", null, null), Duration.ofHours(5));

        assertThat(MlRequest.of(txn("5", 4100, "AE", "d1", null, null), profile).txnsLastHour()).isEqualTo(1);
    }

    @Test
    void anOutOfOrderTimestampNeverGivesNegativeSeconds() {
        CustomerProfile profile = CustomerProfile.empty().with(txn("5", 100, "AE", "d1", null, null), KEEP);
        assertThat(MlRequest.of(txn("5", 50, "AE", "d1", null, null), profile).secondsSinceLastTxn()).isEqualTo(0.0);
    }
}

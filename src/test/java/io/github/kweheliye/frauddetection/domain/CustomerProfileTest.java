package io.github.kweheliye.frauddetection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.kweheliye.frauddetection.streams.FraudTopology;

class CustomerProfileTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private Transaction txn(String amount, long seconds, String country, Double lat, Double lon) {
        return new Transaction("t", "c1", "shop", new BigDecimal(amount), country, "d1", T0.plusSeconds(seconds), lat, lon);
    }

    @Test
    void newCustomerHasNoAverage() {
        assertThat(CustomerProfile.empty().averageAmount()).isNull();
    }

    @Test
    void rememberedAverageIsTheMeanOfAllEarlierAmounts() {
        CustomerProfile profile = CustomerProfile.empty()
                .with(txn("10.00", 0, "AE", null, null), Duration.ofHours(1))
                .with(txn("30.00", 60, "AE", null, null), Duration.ofHours(1));
        assertThat(profile.transactionCount()).isEqualTo(2L);
        assertThat(profile.averageAmount()).isEqualByComparingTo("20");
    }

    @Test
    void remembersTheLastLocationAndCountry() {
        CustomerProfile profile = CustomerProfile.empty().with(txn("5", 0, "GB", 51.5, -0.1), Duration.ofHours(1));
        assertThat(profile.lastCountry()).isEqualTo("GB");
        assertThat(profile.lastLatitude()).isEqualTo(51.5);
        assertThat(profile.lastLongitude()).isEqualTo(-0.1);
    }

    @Test
    void profilesSavedBeforeTheNewFieldsWereAddedStillLoad() {
        // The shape stored by an earlier version of the app, in the Kafka Streams state store.
        String old = "{\"lastCountry\":\"AE\",\"lastSeen\":\"2026-01-01T10:00:00Z\",\"devices\":[\"d1\"],"
                + "\"timestamps\":[\"2026-01-01T10:00:00Z\"]}";
        CustomerProfile profile = FraudTopology.json(CustomerProfile.class).deserializer().deserialize("t", old.getBytes());

        assertThat(profile.amountSum()).isEqualByComparingTo("0");
        assertThat(profile.transactionCount()).isZero();
        assertThat(profile.with(txn("10", 60, "AE", null, null), Duration.ofHours(1)).transactionCount()).isEqualTo(1L);
    }

    @Test
    void survivesBeingWrittenToAndReadFromTheStateStore() {
        var serde = FraudTopology.json(CustomerProfile.class);
        CustomerProfile profile = CustomerProfile.empty().with(txn("12.50", 0, "AE", 25.2, 55.3), Duration.ofHours(1));
        CustomerProfile back = serde.deserializer().deserialize("t", serde.serializer().serialize("t", profile));
        assertThat(back).isEqualTo(profile);
    }
}

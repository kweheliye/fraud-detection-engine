package io.github.kweheliye.frauddetection.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What we remember about a customer. Immutable: with(...) returns an updated copy. */
public record CustomerProfile(
        String lastCountry,
        Instant lastSeen,
        Double lastLatitude,
        Double lastLongitude,
        Set<String> devices,
        List<Instant> timestamps,
        BigDecimal amountSum,
        Long transactionCount) {

    public CustomerProfile {
        // Profiles saved before these fields existed are read back with nulls.
        if (amountSum == null) {
            amountSum = BigDecimal.ZERO;
        }
        if (transactionCount == null) {
            transactionCount = 0L;
        }
    }

    public static CustomerProfile empty() {
        return new CustomerProfile(null, null, null, null, Set.of(), List.of(), BigDecimal.ZERO, 0L);
    }

    /** Average of all earlier amounts, or null for a customer we have not seen before. */
    public BigDecimal averageAmount() {
        if (transactionCount == 0) {
            return null;
        }
        return amountSum.divide(BigDecimal.valueOf(transactionCount), 4, RoundingMode.HALF_UP);
    }

    public CustomerProfile with(Transaction txn, Duration retention) {
        Instant now = txn.timestamp();

        List<Instant> kept = new ArrayList<>();
        for (Instant t : timestamps) {
            if (!t.isBefore(now.minus(retention))) {
                kept.add(t);
            }
        }
        kept.add(now);

        Set<String> allDevices = new HashSet<>(devices);
        allDevices.add(txn.deviceId());

        return new CustomerProfile(txn.country(), now, txn.latitude(), txn.longitude(), allDevices, kept,
                amountSum.add(txn.amount()), transactionCount + 1);
    }
}

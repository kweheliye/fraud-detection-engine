package io.github.kweheliye.frauddetection.ml;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;

/**
 * What the ML sidecar is asked (see ml-sidecar/README.md). The values compare this transaction
 * with the customer's history. Anything unknown is null, never a made-up number.
 */
public record MlRequest(
        double amount,
        @JsonProperty("customer_avg_amount") Double customerAvgAmount,
        @JsonProperty("seconds_since_last_txn") Double secondsSinceLastTxn,
        @JsonProperty("distance_from_last_km") Double distanceFromLastKm,
        @JsonProperty("country_changed") Boolean countryChanged,
        @JsonProperty("txns_last_hour") int txnsLastHour,
        @JsonProperty("is_new_device") boolean newDevice,
        Instant timestamp) {

    private static final Duration HOUR = Duration.ofHours(1);

    public static MlRequest of(Transaction txn, CustomerProfile profile) {
        Instant now = txn.timestamp();

        Double average = profile.averageAmount() == null ? null : profile.averageAmount().doubleValue();
        Double secondsSince = profile.lastSeen() == null ? null
                : Math.max(0, Duration.between(profile.lastSeen(), now).toMillis() / 1000.0);
        Boolean countryChanged = profile.lastCountry() == null ? null
                : !profile.lastCountry().equalsIgnoreCase(txn.country());

        Double distance = null;
        if (profile.lastLatitude() != null && profile.lastLongitude() != null
                && txn.latitude() != null && txn.longitude() != null) {
            distance = haversineKm(profile.lastLatitude(), profile.lastLongitude(), txn.latitude(), txn.longitude());
        }

        // Earlier transactions in the last hour, not counting this one.
        int lastHour = (int) profile.timestamps().stream()
                .filter(t -> !t.isAfter(now) && t.isAfter(now.minus(HOUR)))
                .count();

        return new MlRequest(txn.amount().doubleValue(), average, secondsSince, distance, countryChanged,
                lastHour, !profile.devices().contains(txn.deviceId()), now);
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 6371 * 2 * Math.asin(Math.sqrt(a));
    }
}

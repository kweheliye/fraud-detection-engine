package io.github.kweheliye.frauddetection.domain.rules;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;

@Component
@ConditionalOnProperty(name = "fraud.rules.geo.enabled", havingValue = "true", matchIfMissing = true)
public class GeoMismatchRule implements Rule {

    private final String name;
    private final int weight;
    private final Duration window;

    public GeoMismatchRule(@Value("${fraud.rules.geo.name}") String name,
                        @Value("${fraud.rules.geo.weight}") int weight,
                           @Value("${fraud.rules.geo.window}") Duration window) {
        this.name = name;
        this.weight = weight;
        this.window = window;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public boolean evaluate(Transaction txn, CustomerProfile profile) {
        if (profile.lastSeen() == null || txn.country().equalsIgnoreCase(profile.lastCountry())) {
            return false;
        }
        Duration gap = Duration.between(profile.lastSeen(), txn.timestamp());
        return gap.compareTo(window) < 0;
    }
}
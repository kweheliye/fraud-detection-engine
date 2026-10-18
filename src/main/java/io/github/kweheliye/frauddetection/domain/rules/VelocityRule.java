package io.github.kweheliye.frauddetection.domain.rules;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;

@Component
@ConditionalOnProperty(name = "fraud.rules.velocity.enabled", havingValue = "true", matchIfMissing = true)
public class VelocityRule implements Rule {

    private final String name;
    private final int weight;
    private final int maxTransactions;
    private final Duration window;

    public VelocityRule(@Value("${fraud.rules.velocity.name}") String name,
                        @Value("${fraud.rules.velocity.weight}") int weight,
                        @Value("${fraud.rules.velocity.max-transactions}") int maxTransactions,
                        @Value("${fraud.rules.velocity.window}") Duration window) {
        this.name = name;
        this.weight = weight;
        this.maxTransactions = maxTransactions;
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
        Instant from = txn.timestamp().minus(window);
        long earlier = profile.timestamps().stream().filter(t -> !t.isBefore(from)).count();
        return earlier + 1 > maxTransactions;   // the current transaction counts too
    }
}
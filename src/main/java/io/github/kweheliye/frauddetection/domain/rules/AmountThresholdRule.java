package io.github.kweheliye.frauddetection.domain.rules;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;

@Component
@ConditionalOnProperty(name = "fraud.rules.amount.enabled", havingValue = "true", matchIfMissing = true)
public class AmountThresholdRule implements Rule {

    private final String name;
    private final int weight;
    private final BigDecimal threshold;

    public AmountThresholdRule(@Value("${fraud.rules.amount.name}") String name,
                        @Value("${fraud.rules.amount.weight}") int weight, @Value("${fraud.rules.amount.threshold}") BigDecimal threshold) {
        this.name = name;
        this.weight = weight;
        this.threshold = threshold;
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
        return txn.amount().compareTo(threshold) > 0;
    }
}
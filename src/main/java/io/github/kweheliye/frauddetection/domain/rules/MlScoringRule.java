package io.github.kweheliye.frauddetection.domain.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;
import io.github.kweheliye.frauddetection.ml.MlClient;
import io.github.kweheliye.frauddetection.ml.MlRequest;

/** Fires when the ML sidecar thinks the transaction is fraud. Off by default (needs the sidecar). */
@Component
@ConditionalOnProperty(name = "fraud.rules.ml.enabled", havingValue = "true")
public class MlScoringRule implements Rule {

    private final MlClient client;
    private final String name;
    private final int weight;
    private final double threshold;

    public MlScoringRule(MlClient client,
                         @Value("${fraud.rules.ml.name}") String name,
                         @Value("${fraud.rules.ml.weight}") int weight,
                         @Value("${fraud.rules.ml.threshold}") double threshold) {
        this.client = client;
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
        var score = client.score(MlRequest.of(txn, profile));
        return score.isPresent() && score.getAsDouble() >= threshold;
    }
}

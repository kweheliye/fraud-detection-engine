package io.github.kweheliye.frauddetection.streams;

import java.time.Duration;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Decision;
import io.github.kweheliye.frauddetection.domain.ScoredTransaction;
import io.github.kweheliye.frauddetection.domain.Transaction;
import io.github.kweheliye.frauddetection.domain.rules.RuleEngine;

/** Scores one transaction against the customer's remembered profile, then updates the profile. */
public class ScoreProcessor implements Processor<String, Transaction, String, ScoredTransaction> {

    private final RuleEngine ruleEngine;
    private final Duration retention;

    private ProcessorContext<String, ScoredTransaction> context;
    private KeyValueStore<String, CustomerProfile> profiles;

    ScoreProcessor(RuleEngine ruleEngine, Duration retention) {
        this.ruleEngine = ruleEngine;
        this.retention = retention;
    }

    @Override
    public void init(ProcessorContext<String, ScoredTransaction> context) {
        this.context = context;
        this.profiles = context.getStateStore(FraudTopology.PROFILES);
    }

    @Override
    public void process(Record<String, Transaction> record) {
        Transaction txn = record.value();

        CustomerProfile profile = profiles.get(record.key());
        if (profile == null) {
            profile = CustomerProfile.empty();
        }

        Decision decision = ruleEngine.evaluate(txn, profile);
        profiles.put(record.key(), profile.with(txn, retention));

        context.forward(record.withValue(new ScoredTransaction(txn, decision)));
    }
}
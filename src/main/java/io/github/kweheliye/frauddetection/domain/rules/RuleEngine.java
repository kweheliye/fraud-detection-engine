package io.github.kweheliye.frauddetection.domain.rules;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Decision;
import io.github.kweheliye.frauddetection.domain.RiskLevel;
import io.github.kweheliye.frauddetection.domain.Transaction;

@Component
public class RuleEngine {

    private final List<Rule> rules;
    private final int mediumFrom;
    private final int highFrom;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RuleEngine.class);

    public RuleEngine(List<Rule> rules, @Value("${fraud.medium-from}") int mediumFrom, @Value("${fraud.high-from}") int highFrom) {
        this.rules = rules;
        this.mediumFrom = mediumFrom;
        this.highFrom = highFrom;
        log.info("Active fraud rules: {}", rules.stream().map(Rule::name).toList());
    }

    public Decision evaluate(Transaction txn, CustomerProfile profile) {
        int total = 0;
        List<String> triggered = new ArrayList<>();

        for (Rule rule : rules) {
            if (rule.evaluate(txn, profile)) {
                total += rule.weight();
                triggered.add(rule.name());
            }
        }

        int score = Math.min(total, 100);
        RiskLevel level = score >= highFrom ? RiskLevel.HIGH
                : score >= mediumFrom ? RiskLevel.MEDIUM
                : RiskLevel.LOW;
        return new Decision(score, level, triggered);
    }
}
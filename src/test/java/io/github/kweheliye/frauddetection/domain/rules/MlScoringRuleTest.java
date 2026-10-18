package io.github.kweheliye.frauddetection.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;
import io.github.kweheliye.frauddetection.ml.MlClient;
import io.github.kweheliye.frauddetection.ml.MlRequest;

class MlScoringRuleTest {

    private static final Transaction TXN = new Transaction("t", "c1", "shop", new BigDecimal("50"), "AE", "d1",
            Instant.parse("2026-01-01T10:00:00Z"), null, null);

    private MlScoringRule ruleWithScore(OptionalDouble score) {
        MlClient fake = new MlClient("http://localhost:1", Duration.ofMillis(100)) {
            @Override
            public OptionalDouble score(MlRequest request) {
                return score;
            }
        };
        return new MlScoringRule(fake, "ML_SCORING", 35, 0.5);
    }

    @Test
    void firesWhenTheScoreReachesTheThreshold() {
        assertThat(ruleWithScore(OptionalDouble.of(0.5)).evaluate(TXN, CustomerProfile.empty())).isTrue();
        assertThat(ruleWithScore(OptionalDouble.of(0.97)).evaluate(TXN, CustomerProfile.empty())).isTrue();
    }

    @Test
    void doesNotFireBelowTheThreshold() {
        assertThat(ruleWithScore(OptionalDouble.of(0.49)).evaluate(TXN, CustomerProfile.empty())).isFalse();
    }

    @Test
    void doesNotFireWhenTheSidecarGaveNoAnswer() {
        assertThat(ruleWithScore(OptionalDouble.empty()).evaluate(TXN, CustomerProfile.empty())).isFalse();
    }

    @Test
    void usesTheConfiguredNameAndWeight() {
        MlScoringRule rule = ruleWithScore(OptionalDouble.empty());
        assertThat(rule.name()).isEqualTo("ML_SCORING");
        assertThat(rule.weight()).isEqualTo(35);
    }
}

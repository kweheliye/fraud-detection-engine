package io.github.kweheliye.frauddetection.streams;

import java.time.Duration;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;

import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.RiskLevel;
import io.github.kweheliye.frauddetection.domain.ScoredTransaction;
import io.github.kweheliye.frauddetection.domain.Transaction;
import io.github.kweheliye.frauddetection.domain.rules.RuleEngine;

/**
 * Wires the stream: transactions.raw -> ScoreProcessor -> transactions.scored (all)
 *                                                      -> transactions.flagged (HIGH only)
 * Records must be keyed by customerId so each customer's state stays in one partition.
 */
@Configuration
@EnableKafkaStreams
public class FraudTopology {

    static final String PROFILES = "customer-profiles";

    private final RuleEngine ruleEngine;
    private final Duration retention;
    private final String raw;
    private final String scored;
    private final String flagged;

    public FraudTopology(RuleEngine ruleEngine,
                         @Value("${fraud.profile-retention}") Duration retention,
                         @Value("${fraud.topics.raw}") String raw,
                         @Value("${fraud.topics.scored}") String scored,
                         @Value("${fraud.topics.flagged}") String flagged) {
        this.ruleEngine = ruleEngine;
        this.retention = retention;
        this.raw = raw;
        this.scored = scored;
        this.flagged = flagged;
    }

    @Bean
    public KStream<String, ScoredTransaction> fraudStream(StreamsBuilder builder) {
        Serde<String> key = Serdes.String();
        Serde<ScoredTransaction> out = json(ScoredTransaction.class);

        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(PROFILES), key, json(CustomerProfile.class)));

        KStream<String, ScoredTransaction> result = builder
                .stream(raw, Consumed.with(key, json(Transaction.class)))
                .process(() -> new ScoreProcessor(ruleEngine, retention), PROFILES);

        result.to(scored, Produced.with(key, out));
        result.filter((customerId, r) -> r.decision().riskLevel() == RiskLevel.HIGH)
                .to(flagged, Produced.with(key, out));
        return result;
    }

    /** JSON serde from spring-kafka (Jackson 3). */
    public static <T> Serde<T> json(Class<T> type) {
        return new JacksonJsonSerde<T>(type).noTypeInfo();
    }
}
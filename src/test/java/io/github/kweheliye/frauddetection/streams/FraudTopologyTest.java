package io.github.kweheliye.frauddetection.streams;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import io.github.kweheliye.frauddetection.domain.RiskLevel;
import io.github.kweheliye.frauddetection.domain.ScoredTransaction;
import io.github.kweheliye.frauddetection.domain.Transaction;
import io.github.kweheliye.frauddetection.domain.rules.*;

class FraudTopologyTest {

    private static final String RAW = "transactions.raw";
    private static final String SCORED = "transactions.scored";
    private static final String FLAGGED = "transactions.flagged";
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private TopologyTestDriver driver;
    private TestInputTopic<String, Transaction> raw;
    private TestOutputTopic<String, ScoredTransaction> scored;
    private TestOutputTopic<String, ScoredTransaction> flagged;

    private void start() throws Exception {
        RuleEngine engine = new RuleEngine(List.of(
                new AmountThresholdRule("AMOUNT_THRESHOLD", 40, new BigDecimal("10000")),
                new GeoMismatchRule("GEO_MISMATCH", 25, Duration.ofHours(1)),
                new VelocityRule("VELOCITY", 25, 5, Duration.ofSeconds(60)),
                new NewDeviceRule("NEW_DEVICE", 10)), 30, 70);

        StreamsBuilder builder = new StreamsBuilder();
        new FraudTopology(engine, Duration.ofHours(1), RAW, SCORED, FLAGGED).fraudStream(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("streams-test").toString());
        driver = new TopologyTestDriver(builder.build(), props);

        raw = driver.createInputTopic(RAW, Serdes.String().serializer(), FraudTopology.json(Transaction.class).serializer());
        scored = driver.createOutputTopic(SCORED, Serdes.String().deserializer(), FraudTopology.json(ScoredTransaction.class).deserializer());
        flagged = driver.createOutputTopic(FLAGGED, Serdes.String().deserializer(), FraudTopology.json(ScoredTransaction.class).deserializer());
    }

    private void send(String id, String customer, String amount, String country, String device, long secondsAfterStart) {
        Transaction txn = new Transaction(id, customer, "shop", new BigDecimal(amount), country, device, T0.plusSeconds(secondsAfterStart), null, null);
        raw.pipeInput(customer, txn);
    }

    @Test
    void firstTransactionOnlyTriggersNewDevice() throws Exception {
        start();
        send("t1", "c1", "50", "AE", "d1", 0);

        ScoredTransaction result = scored.readValue();
        assertThat(result.decision().score()).isEqualTo(10);
        assertThat(result.decision().riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.decision().triggeredRules()).containsExactly("NEW_DEVICE");
        assertThat(flagged.isEmpty()).isTrue();
        driver.close();
    }

    @Test
    void profileIsRememberedBetweenTransactions() throws Exception {
        start();
        send("t1", "c1", "50", "AE", "d1", 0);
        send("t2", "c1", "50", "AE", "d1", 10);

        scored.readValue();
        assertThat(scored.readValue().decision().score()).isEqualTo(0);
        driver.close();
    }

    @Test
    void customersDoNotShareState() throws Exception {
        start();
        send("t1", "c1", "50", "AE", "d1", 0);
        send("t2", "c2", "50", "AE", "d1", 5);

        scored.readValue();
        assertThat(scored.readValue().decision().triggeredRules()).containsExactly("NEW_DEVICE");
        driver.close();
    }

    @Test
    void highRiskGoesToFlaggedTopic() throws Exception {
        start();
        send("t1", "c1", "50", "AE", "d1", 0);
        send("t2", "c1", "20000", "US", "d2", 60);

        scored.readValue();
        ScoredTransaction result = scored.readValue();
        assertThat(result.decision().score()).isEqualTo(75);
        assertThat(result.decision().riskLevel()).isEqualTo(RiskLevel.HIGH);

        ScoredTransaction flaggedTxn = flagged.readValue();
        assertThat(flaggedTxn.transaction().transactionId()).isEqualTo("t2");
        assertThat(flagged.isEmpty()).isTrue();
        driver.close();
    }

    @Test
    void velocityFiresOnSixthTransactionInAMinute() throws Exception {
        start();
        for (int i = 1; i <= 6; i++) {
            send("t" + i, "c1", "5", "AE", "d1", i);
        }
        List<ScoredTransaction> all = scored.readValuesToList();
        assertThat(all.get(4).decision().triggeredRules().contains("VELOCITY")).isFalse();
        assertThat(all.get(5).decision().triggeredRules().contains("VELOCITY")).isTrue();
        driver.close();
    }
}

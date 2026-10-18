package io.github.kweheliye.frauddetection.persistence;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.kweheliye.frauddetection.domain.ScoredTransaction;

/** Listens to the scored and flagged topics and saves what arrives. */
@Component
public class PersistenceListener {

    private final TransactionRepository repository;

    public PersistenceListener(TransactionRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${fraud.topics.scored}", groupId = "persist-scored")
    public void onScored(ScoredTransaction scored) {
        repository.saveScored(scored);
    }

    @KafkaListener(topics = "${fraud.topics.flagged}", groupId = "persist-flagged")
    public void onFlagged(ScoredTransaction scored) {
        repository.saveFlagged(scored);
    }
}

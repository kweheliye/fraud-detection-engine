package io.github.kweheliye.frauddetection.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import io.github.kweheliye.frauddetection.domain.Transaction;

/** Sends a transaction into the pipeline. Scoring happens asynchronously in the stream. */
@Service
public class TransactionSubmissionService {

    private final KafkaTemplate<String, Transaction> kafka;
    private final String rawTopic;

    public TransactionSubmissionService(KafkaTemplate<String, Transaction> kafka,
                                        @Value("${fraud.topics.raw}") String rawTopic) {
        this.kafka = kafka;
        this.rawTopic = rawTopic;
    }

    public void submit(Transaction txn) {
        // Keyed by customerId: all of a customer's transactions go to the same partition.
        kafka.send(rawTopic, txn.customerId(), txn);
    }
}

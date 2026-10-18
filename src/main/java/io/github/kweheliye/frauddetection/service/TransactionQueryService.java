package io.github.kweheliye.frauddetection.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.kweheliye.frauddetection.domain.FlaggedTransaction;
import io.github.kweheliye.frauddetection.domain.StoredTransaction;
import io.github.kweheliye.frauddetection.exception.TransactionNotFoundException;
import io.github.kweheliye.frauddetection.persistence.TransactionRepository;

/** Looks up scored transactions. */
@Service
public class TransactionQueryService {

    static final int MAX_LIMIT = 200;

    private final TransactionRepository repository;

    public TransactionQueryService(TransactionRepository repository) {
        this.repository = repository;
    }

    public StoredTransaction get(String transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    /** Newest first. A null customerId means everyone. */
    public List<StoredTransaction> recent(String customerId, int limit) {
        return repository.findRecent(customerId, clamp(limit));
    }

    public List<FlaggedTransaction> recentFlagged(int limit) {
        return repository.findRecentFlagged(clamp(limit));
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}

package io.github.kweheliye.frauddetection.domain.rules;

import io.github.kweheliye.frauddetection.domain.CustomerProfile;
import io.github.kweheliye.frauddetection.domain.Transaction;

public interface Rule {

    String name();

    int weight();

    boolean evaluate(Transaction txn, CustomerProfile profile);
}
package io.github.kweheliye.frauddetection.domain;

/** A transaction together with the decision the rules made about it. */
public record ScoredTransaction(Transaction transaction, Decision decision) { }
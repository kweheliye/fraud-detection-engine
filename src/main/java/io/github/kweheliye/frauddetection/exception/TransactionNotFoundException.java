package io.github.kweheliye.frauddetection.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String transactionId) {
        super("No transaction " + transactionId);
    }
}

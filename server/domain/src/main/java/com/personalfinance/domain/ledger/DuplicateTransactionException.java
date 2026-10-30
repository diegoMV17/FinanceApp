package com.personalfinance.domain.ledger;

/**
 * The same id recorded twice. Harmless-looking today, but once the phone and
 * the server start syncing, a replayed insert would double a balance silently.
 */
public final class DuplicateTransactionException extends LedgerException {

    private final TransactionId id;

    public DuplicateTransactionException(TransactionId id) {
        super("Transaction " + id + " is already in the ledger");
        this.id = id;
    }

    public TransactionId id() {
        return id;
    }
}

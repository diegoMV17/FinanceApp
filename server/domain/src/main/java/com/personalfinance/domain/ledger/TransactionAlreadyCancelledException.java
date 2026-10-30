package com.personalfinance.domain.ledger;

import java.time.Instant;

/**
 * Cancelling twice is a bug somewhere, not a no-op: either two parts of the app
 * think they own the same correction, or a sync replayed an old instruction.
 * Failing loudly here is cheaper than a balance nobody can explain.
 */
public final class TransactionAlreadyCancelledException extends LedgerException {

    private final TransactionId id;

    public TransactionAlreadyCancelledException(Transaction transaction) {
        this(transaction.id(), transaction.cancellation().orElseThrow().at());
    }

    /**
     * For a caller that holds what was stored rather than the whole
     * transaction, such as a store that only read the header.
     */
    public TransactionAlreadyCancelledException(TransactionId id, Instant cancelledAt) {
        super("Transaction %s was already cancelled on %s".formatted(id, cancelledAt));
        this.id = id;
    }

    public TransactionId id() {
        return id;
    }
}

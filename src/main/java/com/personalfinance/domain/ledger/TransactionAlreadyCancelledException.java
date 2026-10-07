package com.personalfinance.domain.ledger;

/**
 * Cancelling twice is a bug somewhere, not a no-op: either two parts of the app
 * think they own the same correction, or a sync replayed an old instruction.
 * Failing loudly here is cheaper than a balance nobody can explain.
 */
public final class TransactionAlreadyCancelledException extends LedgerException {

    private final TransactionId id;

    public TransactionAlreadyCancelledException(Transaction transaction) {
        super("Transaction %s was already cancelled on %s"
                .formatted(transaction.id(),
                        transaction.cancellation().orElseThrow().at()));
        this.id = transaction.id();
    }

    public TransactionId id() {
        return id;
    }
}

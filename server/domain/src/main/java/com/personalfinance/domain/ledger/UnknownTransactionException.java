package com.personalfinance.domain.ledger;

/** Asked to cancel or amend something the book has never seen. */
public final class UnknownTransactionException extends LedgerException {

    private final TransactionId id;

    public UnknownTransactionException(TransactionId id) {
        super("The ledger holds no transaction " + id);
        this.id = id;
    }

    public TransactionId id() {
        return id;
    }
}

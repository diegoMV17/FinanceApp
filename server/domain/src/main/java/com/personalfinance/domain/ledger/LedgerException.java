package com.personalfinance.domain.ledger;

/** Base type for every rule the ledger refuses to break. */
public abstract class LedgerException extends RuntimeException {

    protected LedgerException(String message) {
        super(message);
    }
}

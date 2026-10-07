package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;

/** The entries of a transaction did not sum to zero. */
public final class UnbalancedTransactionException extends LedgerException {

    private final Money difference;

    public UnbalancedTransactionException(Money difference) {
        super("Entries must sum to zero; they are off by " + difference);
        this.difference = difference;
    }

    public Money difference() {
        return difference;
    }
}

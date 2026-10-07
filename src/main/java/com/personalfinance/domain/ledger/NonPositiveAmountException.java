package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;

/**
 * Operations are asked for the amount as the person says it: "I spent 35,000".
 * The sign is the ledger's job, not the caller's.
 */
public final class NonPositiveAmountException extends LedgerException {

    private final Money amount;

    public NonPositiveAmountException(String operation, Money amount) {
        super("%s needs a positive amount, got %s".formatted(operation, amount));
        this.amount = amount;
    }

    public Money amount() {
        return amount;
    }
}

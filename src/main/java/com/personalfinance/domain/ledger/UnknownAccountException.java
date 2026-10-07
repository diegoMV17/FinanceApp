package com.personalfinance.domain.ledger;

/** Asked about an account the catalogue has never held. */
public final class UnknownAccountException extends LedgerException {

    private final AccountId id;

    public UnknownAccountException(AccountId id) {
        super("The chart holds no account " + id);
        this.id = id;
    }

    public AccountId id() {
        return id;
    }
}

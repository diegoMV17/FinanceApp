package com.personalfinance.domain.ledger;

/** An entry that moves nothing is noise in the ledger. */
public final class ZeroAmountEntryException extends LedgerException {

    private final Account account;

    public ZeroAmountEntryException(Account account) {
        super("An entry on " + account.name() + " cannot have a zero amount");
        this.account = account;
    }

    public Account account() {
        return account;
    }
}

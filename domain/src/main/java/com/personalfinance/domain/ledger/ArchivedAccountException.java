package com.personalfinance.domain.ledger;

/** Archived accounts keep their history but take no new entries. */
public final class ArchivedAccountException extends LedgerException {

    private final Account account;

    public ArchivedAccountException(Account account) {
        super(account.name() + " is archived and cannot take new entries");
        this.account = account;
    }

    public Account account() {
        return account;
    }
}

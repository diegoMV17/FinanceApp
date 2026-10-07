package com.personalfinance.domain.ledger;

import java.util.Arrays;
import java.util.List;

/**
 * An operation was handed the wrong kind of account, for example paying for
 * lunch "out of" an income category.
 */
public final class UnexpectedAccountTypeException extends LedgerException {

    private final Account account;
    private final List<AccountType> expected;

    public UnexpectedAccountTypeException(Account account, AccountType... expected) {
        super("%s is %s; expected one of %s"
                .formatted(account.name(), account.type(), Arrays.toString(expected)));
        this.account = account;
        this.expected = List.of(expected);
    }

    public Account account() {
        return account;
    }

    public List<AccountType> expected() {
        return expected;
    }
}

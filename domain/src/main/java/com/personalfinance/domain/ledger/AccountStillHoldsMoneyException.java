package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;

/**
 * Archiving an account that still holds money would make the money vanish.
 * Move it out or reconcile it to zero first.
 */
public final class AccountStillHoldsMoneyException extends LedgerException {

    private final Account account;
    private final Money balance;

    public AccountStillHoldsMoneyException(Account account, Money balance) {
        super("%s still holds %s; move it out before archiving"
                .formatted(account.name(), account.presentationOf(balance)));
        this.account = account;
        this.balance = balance;
    }

    public Account account() {
        return account;
    }

    public Money balance() {
        return balance;
    }
}

package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.util.Objects;

/**
 * One line of a transaction: this account moved by this amount.
 *
 * <p>An entry has no identity of its own. It is a value inside the
 * {@link Transaction} aggregate; the database gives it a primary key, the
 * domain does not need one.
 */
public record Entry(Account account, Money amount) {

    public Entry {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");

        if (amount.isZero()) {
            throw new ZeroAmountEntryException(account);
        }
        if (account.isArchived()) {
            throw new ArchivedAccountException(account);
        }
    }

    public static Entry of(Account account, Money amount) {
        return new Entry(account, amount);
    }

    /** Reads naturally at call sites: debit(cash, Money.ofUnits(35_000)). */
    public static Entry debit(Account account, Money amount) {
        return new Entry(account, amount);
    }

    public static Entry credit(Account account, Money amount) {
        return new Entry(account, amount.negated());
    }

    public boolean touches(Account other) {
        return account.equals(other);
    }

    @Override
    public String toString() {
        return "%s %s".formatted(account.name(), amount);
    }
}

package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.util.Objects;

/**
 * One line of a transaction: this account moved by this amount.
 *
 * <p>An entry has no identity of its own. It is a value inside the
 * {@link Transaction} aggregate; the database gives it a primary key, the
 * domain does not need one.
 *
 * <p>The constructor is private so that there is no way to build an entry that
 * skips a rule: new entries go through {@link #of}, and the single exception,
 * {@link #rehydrated}, exists for history that was already valid when stored.
 */
public final class Entry {

    /**
     * Ten thousand million pesos, which is far more than this book will ever
     * see and far less than what breaks the arithmetic. See
     * {@link AmountOutOfRangeException} for why the ceiling exists at all.
     */
    public static final long MAX_ABSOLUTE_CENTS = 1_000_000_000_000L;

    private final Account account;
    private final Money amount;

    private Entry(Account account, Money amount) {
        this.account = Objects.requireNonNull(account, "account");
        this.amount = Objects.requireNonNull(amount, "amount");

        if (amount.isZero()) {
            throw new ZeroAmountEntryException(account);
        }
        // Comparado contra los dos extremos y no con Math.abs: el valor
        // absoluto de Long.MIN_VALUE sigue siendo negativo y pasaría.
        if (amount.cents() > MAX_ABSOLUTE_CENTS || amount.cents() < -MAX_ABSOLUTE_CENTS) {
            throw new AmountOutOfRangeException(account, amount, MAX_ABSOLUTE_CENTS);
        }
    }

    public static Entry of(Account account, Money amount) {
        Entry entry = new Entry(account, amount);
        if (account.isArchived()) {
            throw new ArchivedAccountException(account);
        }
        return entry;
    }

    /** Reads naturally at call sites: debit(cash, Money.ofUnits(35_000)). */
    public static Entry debit(Account account, Money amount) {
        return of(account, amount);
    }

    public static Entry credit(Account account, Money amount) {
        return of(account, amount.negated());
    }

    /**
     * An entry as it was stored. It may point at an account that has been
     * archived since: archiving stops new movements, it does not rewrite the
     * ones that already happened. Everything else is still checked, the amount
     * limit included: a row somebody edited in the database has to fail here,
     * on load, and not in every balance from then on.
     */
    public static Entry rehydrated(Account account, Money amount) {
        return new Entry(account, amount);
    }

    public Account account() {
        return account;
    }

    public Money amount() {
        return amount;
    }

    public boolean touches(Account other) {
        return account.equals(other);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Entry entry
                && account.equals(entry.account)
                && amount.equals(entry.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, amount);
    }

    @Override
    public String toString() {
        return "%s %s".formatted(account.name(), amount);
    }
}

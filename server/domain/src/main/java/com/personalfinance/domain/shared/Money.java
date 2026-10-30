package com.personalfinance.domain.shared;

import java.util.Objects;

/**
 * An amount of money, stored as whole cents.
 *
 * <p>Never use floating point for money. {@code 0.1 + 0.2} is not {@code 0.3},
 * and after a few hundred transactions the balance drifts in ways that are
 * impossible to explain to the person reading the screen.
 *
 * <p>The sign is meaningful: it says which side of the double entry this amount
 * sits on, not whether the amount is "good" or "bad". See {@link
 * com.personalfinance.domain.ledger.BalanceNature}.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    private static final int CENTS_PER_UNIT = 100;

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /** Convenience for tests and for the UI, which speaks in whole pesos. */
    public static Money ofUnits(long units) {
        return new Money(Math.multiplyExact(units, CENTS_PER_UNIT));
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(Math.addExact(this.cents, other.cents));
    }

    public Money minus(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(Math.subtractExact(this.cents, other.cents));
    }

    public Money negated() {
        return new Money(Math.negateExact(cents));
    }

    public Money times(int factor) {
        return new Money(Math.multiplyExact(cents, factor));
    }

    public boolean isZero() {
        return cents == 0;
    }

    public boolean isPositive() {
        return cents > 0;
    }

    public boolean isNegative() {
        return cents < 0;
    }

    public static Money sum(Iterable<Money> amounts) {
        Money total = ZERO;
        for (Money amount : amounts) {
            total = total.plus(amount);
        }
        return total;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(this.cents, other.cents);
    }

    @Override
    public String toString() {
        return "%d.%02d".formatted(cents / CENTS_PER_UNIT, Math.abs(cents % CENTS_PER_UNIT));
    }
}

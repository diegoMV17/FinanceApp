package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.YearMonth;
import java.util.Objects;

/**
 * HU27. How a month is going against its limit.
 *
 * <p>Derived, never stored, like every other number here: the limit comes from
 * the {@link Budget} and the spending from the entries. A stored "spent so far"
 * would stop being true the first time a movement was cancelled.
 *
 * <p>The thresholds live here and travel already decided to the edge. HU27 asks
 * for two different things — getting close is a warning, going over is a fact —
 * and if the phone worked them out for itself, the server and the phone would
 * end up warning on different days about the same month.
 *
 * @param spent what actually went out, with the sign the person reads, so an
 *              expense comes in positive
 */
public record BudgetProgress(
        BudgetId budget,
        AccountId category,
        String categoryName,
        YearMonth month,
        Money limit,
        Money spent) {

    /** From here on it is worth saying something before the month ends. */
    public static final long NEAR_THE_LIMIT_PERCENTAGE = 80;

    /**
     * A percentage nobody can draw is no use.
     *
     * <p>It is also what keeps the arithmetic inside a {@code long}: a limit of
     * one cent against a book-sized spend would overflow a plain multiply by a
     * hundred, and an overflow in a money application is a 500 at the worst
     * possible moment.
     */
    public static final long HIGHEST_REPORTED_PERCENTAGE = 10_000;

    public BudgetProgress {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(categoryName, "categoryName");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(spent, "spent");
    }

    /**
     * What share of the limit is gone, rounded down.
     *
     * <p>Split in two so that it cannot overflow: the whole part is divided
     * first and only the remainder — which is smaller than the limit — gets
     * multiplied. Past the reported ceiling the exact number stops meaning
     * anything, so it is capped rather than computed.
     *
     * <p>It can come out negative, and that is the honest answer: a refunded
     * month spent less than nothing.
     */
    public long percentageUsed() {
        long limitInCents = limit.cents();
        long whole = spent.cents() / limitInCents;

        if (whole >= HIGHEST_REPORTED_PERCENTAGE / 100) {
            return HIGHEST_REPORTED_PERCENTAGE;
        }
        if (whole <= -HIGHEST_REPORTED_PERCENTAGE / 100) {
            return -HIGHEST_REPORTED_PERCENTAGE;
        }
        return whole * 100 + (spent.cents() % limitInCents) * 100 / limitInCents;
    }

    /** Spending exactly the limit is spending the limit, not going over it. */
    public boolean isExceeded() {
        return spent.compareTo(limit) > 0;
    }

    /** Close, and not yet over: the two warnings are mutually exclusive. */
    public boolean isNearTheLimit() {
        return !isExceeded() && percentageUsed() >= NEAR_THE_LIMIT_PERCENTAGE;
    }

    /** What is left, which goes negative once the limit is passed. */
    public Money remaining() {
        return limit.minus(spent);
    }
}

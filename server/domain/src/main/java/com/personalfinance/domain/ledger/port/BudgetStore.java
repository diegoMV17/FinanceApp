package com.personalfinance.domain.ledger.port;

import com.personalfinance.domain.ledger.Budget;
import com.personalfinance.domain.ledger.Chart;
import java.time.Instant;
import java.util.List;

/**
 * Where the budgets live.
 *
 * <p>There is no {@code delete} and no way to change a limit: a budget is
 * replaced, never edited, so the only two things this needs to do are write a
 * new one and write a replacement together with the mark on the one it takes
 * the place of.
 *
 * <p>{@link #all} takes the chart for the same reason the other stores take
 * theirs: a budget points at the very category the chart handed out, never at a
 * copy of it. Two {@code Account} objects called "Alimentación" are two
 * categories, and the spending of one would be measured against the limit of
 * the other.
 */
public interface BudgetStore {

    /** A limit that nothing came before. */
    void set(Budget budget);

    /**
     * The replacement and the mark on the previous one, in a single commit.
     *
     * <p>Both halves or neither: with only the first, two limits would be live
     * for the same month; with only the second, the month would have none.
     */
    void replace(Budget replacement, Instant when);

    /** Everything ever set, superseded included, oldest first. */
    List<Budget> all(Chart chart);
}

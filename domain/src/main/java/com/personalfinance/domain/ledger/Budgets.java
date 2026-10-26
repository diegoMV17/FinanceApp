package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * HU26. Every limit ever set, the live ones and the ones that were replaced.
 *
 * <p>The same shape as the chart and the directory: things go in, nothing comes
 * out. A limit that stops counting keeps its row with the moment it stopped, so
 * a report of March can still say what the plan was before it was raised.
 *
 * <p>One live limit per category and per month, and that invariant lives here
 * because it is the only place that can see all of them at once. The schema
 * keeps a partial unique index saying the same thing, which is the net that is
 * still there when somebody opens a {@code psql}.
 */
public final class Budgets {

    private final Map<BudgetId, Budget> budgets = new LinkedHashMap<>();

    /**
     * HU26. Sets the limit of a category for a month.
     *
     * <p>One method for what the owner experiences as one action. If nothing
     * was set, this records it; if something was, this supersedes it and
     * records the replacement pointing back at it. The caller finds out which
     * of the two happened by asking the result what it replaced, and that is
     * what decides how it gets stored and how it gets written down.
     *
     * @param when the moment the previous limit stopped counting
     * @throws DuplicateBudgetException if that id is already in use
     * @throws ArchivedAccountException if the category is archived
     * @throws UnexpectedAccountTypeException if it is not an expense category
     */
    public Budget set(
            BudgetId id, Account category, YearMonth month, Money limit, Instant when) {

        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(when, "when");

        if (budgets.containsKey(id)) {
            throw new DuplicateBudgetException(id);
        }
        // Not in the constructor: a category archived in June must not stop a
        // budget written in March from loading. Same reasoning as linking an
        // account to a person.
        if (category.isArchived()) {
            throw new ArchivedAccountException(category);
        }

        Optional<Budget> previous = liveFor(category, month);
        Budget current = previous
                .map(replaced -> Budget.replacing(replaced, id, limit))
                .orElseGet(() -> Budget.of(id, category, month, limit));

        previous.ifPresent(replaced -> replaced.supersede(when));
        budgets.put(id, current);

        return current;
    }

    /** What the stored rows say. Rehydration only. */
    public Budget add(Budget budget) {
        Objects.requireNonNull(budget, "budget");
        if (budgets.containsKey(budget.id())) {
            throw new DuplicateBudgetException(budget.id());
        }
        budgets.put(budget.id(), budget);
        return budget;
    }

    public Optional<Budget> find(BudgetId id) {
        return Optional.ofNullable(budgets.get(Objects.requireNonNull(id, "id")));
    }

    /** The limit that counts for that category in that month, if there is one. */
    public Optional<Budget> liveFor(Account category, YearMonth month) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(month, "month");

        return budgets.values().stream()
                .filter(Budget::isLive)
                .filter(budget -> budget.isFor(category, month))
                .findFirst();
    }

    /** HU27. The limits that count for that month, in the order they were set. */
    public List<Budget> liveFor(YearMonth month) {
        Objects.requireNonNull(month, "month");

        return budgets.values().stream()
                .filter(Budget::isLive)
                .filter(budget -> budget.month().equals(month))
                .toList();
    }

    /** Everything ever set, superseded included, oldest first. */
    public List<Budget> all() {
        return List.copyOf(budgets.values());
    }
}

package com.personalfinance.domain.ledger;

/**
 * Two different limits were written under the same id.
 *
 * <p>The ids come from the client, so this is the answer to a retry that is not
 * a retry: the same id saying something else. Recording it would leave two
 * budgets claiming to be the same fact, and nobody could tell which one the
 * phone meant.
 */
public final class DuplicateBudgetException extends LedgerException {

    private final BudgetId id;

    public DuplicateBudgetException(BudgetId id) {
        super("A budget already exists under " + id);
        this.id = id;
    }

    public BudgetId id() {
        return id;
    }
}

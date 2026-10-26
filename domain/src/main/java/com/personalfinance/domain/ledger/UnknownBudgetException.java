package com.personalfinance.domain.ledger;

/**
 * A budget was asked for by an id the book does not hold.
 *
 * <p>Reachable only through the store, which never assumes the in-memory book
 * checked first: the aggregate can only ever supersede a limit it is already
 * holding.
 */
public final class UnknownBudgetException extends LedgerException {

    private final BudgetId id;

    public UnknownBudgetException(BudgetId id) {
        super("There is no budget under " + id);
        this.id = id;
    }

    public BudgetId id() {
        return id;
    }
}

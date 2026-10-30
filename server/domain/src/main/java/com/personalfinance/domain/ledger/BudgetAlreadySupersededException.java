package com.personalfinance.domain.ledger;

import java.time.Instant;

/**
 * A limit that had already been replaced was replaced again.
 *
 * <p>"It stopped counting on this date" is a fact; moving it or doing it twice
 * rewrites history. The aggregate cannot produce this on its own — it only ever
 * supersedes the live one — so what this really guards is the store, which
 * never assumes the in-memory book checked first.
 */
public final class BudgetAlreadySupersededException extends LedgerException {

    private final BudgetId id;
    private final Instant at;

    public BudgetAlreadySupersededException(BudgetId id, Instant at) {
        super("The budget %s was already superseded on %s".formatted(id, at));
        this.id = id;
        this.at = at;
    }

    public BudgetId id() {
        return id;
    }

    public Instant at() {
        return at;
    }
}

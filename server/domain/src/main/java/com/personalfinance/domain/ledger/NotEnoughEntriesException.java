package com.personalfinance.domain.ledger;

/** A transaction needs at least two entries: money always comes from somewhere. */
public final class NotEnoughEntriesException extends LedgerException {

    private final int actual;
    private final int required;

    public NotEnoughEntriesException(int actual, int required) {
        super("A transaction needs at least %d entries, got %d".formatted(required, actual));
        this.actual = actual;
        this.required = required;
    }

    public int actual() {
        return actual;
    }

    public int required() {
        return required;
    }
}

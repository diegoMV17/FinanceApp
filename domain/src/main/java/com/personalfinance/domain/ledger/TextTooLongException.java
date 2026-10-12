package com.personalfinance.domain.ledger;

/**
 * A field arrived longer than the book stores.
 *
 * <p>The text itself is deliberately left out of the message. It came from
 * outside, it may be megabytes long, and this message is what ends up in a log
 * line and, later, in an API response.
 */
public final class TextTooLongException extends LedgerException {

    private final String field;
    private final int maximum;

    public TextTooLongException(String field, int maximum) {
        super("A %s cannot be longer than %d characters".formatted(field, maximum));
        this.field = field;
        this.maximum = maximum;
    }

    public String field() {
        return field;
    }

    public int maximum() {
        return maximum;
    }
}

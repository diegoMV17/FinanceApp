package com.personalfinance.domain.ledger;

/**
 * A line break, a tab or a NUL arrived inside a piece of text the book keeps.
 *
 * <p>This is not about tidiness. These three texts are repeated in log lines
 * and in chat messages, and a line break inside a description is a forged log
 * line: "Almuerzo\nINFO movimiento anulado por el sistema" reads as two
 * entries, one of which never happened.
 */
public final class ControlCharactersException extends LedgerException {

    private final String field;

    public ControlCharactersException(String field) {
        super("A %s cannot contain control characters".formatted(field));
        this.field = field;
    }

    public String field() {
        return field;
    }
}

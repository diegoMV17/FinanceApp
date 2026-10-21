package com.personalfinance.domain.ledger;

import java.time.LocalDate;

/**
 * A movement claims to have happened outside the century this book covers.
 *
 * <p>{@link LocalDate} will gladly hold the year 999999999, and a single row
 * like that would sit at the far end of every "as of" query for the rest of the
 * book's life — and nothing here is ever deleted, so it could only be cancelled
 * and never removed. A date like that is a typo or a device with a broken
 * clock, and the cheapest moment to say so is before it is stored.
 */
public final class DateOutOfRangeException extends LedgerException {

    private final LocalDate date;
    private final LocalDate earliest;
    private final LocalDate latest;

    public DateOutOfRangeException(LocalDate date, LocalDate earliest, LocalDate latest) {
        super("%s is outside %s..%s".formatted(date, earliest, latest));
        this.date = date;
        this.earliest = earliest;
        this.latest = latest;
    }

    public LocalDate date() {
        return date;
    }

    public LocalDate earliest() {
        return earliest;
    }

    public LocalDate latest() {
        return latest;
    }
}

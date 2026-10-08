package com.personalfinance.domain.ledger;

import java.util.Objects;

/**
 * The catalogue and the ledger, together.
 *
 * <p>Neither is much use without the other: a ledger holds entries that point
 * at accounts, and those accounts have to be the very instances the chart hands
 * out. Keeping the pair in one place is what stops a caller from loading a book
 * and then asking it about an account from somewhere else.
 */
public record Book(Chart chart, Ledger ledger) {

    public Book {
        Objects.requireNonNull(chart, "chart");
        Objects.requireNonNull(ledger, "ledger");
    }

    public static Book empty() {
        return new Book(new Chart(), new Ledger());
    }
}

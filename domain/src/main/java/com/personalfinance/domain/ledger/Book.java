package com.personalfinance.domain.ledger;

import java.util.Objects;

/**
 * The catalogue, the ledger and the people, together.
 *
 * <p>Neither is much use without the other: a ledger holds entries that point
 * at accounts, and those accounts have to be the very instances the chart hands
 * out. The directory is there for the same reason one step along: an account
 * points at the very person the directory handed out, never at a copy. Keeping
 * the three in one place is what stops a caller from loading a book and then
 * asking it about an account, or a person, from somewhere else.
 */
public record Book(Chart chart, Ledger ledger, Directory directory) {

    public Book {
        Objects.requireNonNull(chart, "chart");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(directory, "directory");
    }

    public static Book empty() {
        return new Book(new Chart(), new Ledger(), new Directory());
    }
}

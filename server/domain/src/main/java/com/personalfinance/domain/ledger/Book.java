package com.personalfinance.domain.ledger;

import java.util.Objects;

/**
 * The catalogue, the ledger, the people and the budgets, together.
 *
 * <p>Neither is much use without the other: a ledger holds entries that point
 * at accounts, and those accounts have to be the very instances the chart hands
 * out. The directory is there for the same reason one step along: an account
 * points at the very person the directory handed out, never at a copy. The
 * budgets are a third step of the same chain, pointing at the very category the
 * chart handed out. Keeping the four in one place is what stops a caller from
 * loading a book and then asking it about an account, a person or a limit from
 * somewhere else.
 */
public record Book(Chart chart, Ledger ledger, Directory directory, Budgets budgets) {

    public Book {
        Objects.requireNonNull(chart, "chart");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(budgets, "budgets");
    }

    public static Book empty() {
        return new Book(new Chart(), new Ledger(), new Directory(), new Budgets());
    }
}

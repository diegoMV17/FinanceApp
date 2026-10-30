package com.personalfinance.domain.ledger;

/**
 * The catalogue was handed an id it already holds.
 *
 * <p>Taking it in would replace the instance behind that id, and then the
 * entries already written would point at one object and the new ones at
 * another: the balance of that account splits in two without a single error.
 * With ids coming from the client, a replayed "open account" is exactly that
 * case.
 */
public final class DuplicateAccountException extends LedgerException {

    private final AccountId id;

    public DuplicateAccountException(AccountId id) {
        super("The chart already holds an account " + id);
        this.id = id;
    }

    public AccountId id() {
        return id;
    }
}

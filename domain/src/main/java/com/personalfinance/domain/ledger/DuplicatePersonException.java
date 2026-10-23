package com.personalfinance.domain.ledger;

/**
 * The directory was handed an id it already holds.
 *
 * <p>Taking it in would replace the instance behind that id, and then the
 * accounts already linked would point at one object and a later link at
 * another: the position of that person splits in two without a single error.
 * With ids coming from the client, a replayed "add this person" is exactly that
 * case.
 */
public final class DuplicatePersonException extends LedgerException {

    private final PersonId id;

    public DuplicatePersonException(PersonId id) {
        super("The directory already holds a person " + id);
        this.id = id;
    }

    public PersonId id() {
        return id;
    }
}

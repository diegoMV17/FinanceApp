package com.personalfinance.domain.ledger;

/** Asked about a person the directory has never held. */
public final class UnknownPersonException extends LedgerException {

    private final PersonId id;

    public UnknownPersonException(PersonId id) {
        super("The directory holds no person " + id);
        this.id = id;
    }

    public PersonId id() {
        return id;
    }
}

package com.personalfinance.domain.ledger;

/**
 * An archived person keeps the accounts they already had, because the history
 * is read through them, but takes no new ones.
 */
public final class ArchivedPersonException extends LedgerException {

    private final Person person;

    public ArchivedPersonException(Person person) {
        super(person.name() + " is archived and takes no new accounts");
        this.person = person;
    }

    public Person person() {
        return person;
    }
}

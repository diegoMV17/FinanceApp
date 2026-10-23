package com.personalfinance.domain.ledger;

/**
 * Two people you can still pick cannot share a name: choosing "Juan" from a
 * list that holds him twice is a coin flip, and the wrong half of the time the
 * debt lands on the wrong person.
 *
 * <p>An archived person may share a name with a live one. The Juan you stopped
 * lending to last year should not stop you writing down a new Juan today.
 */
public final class DuplicatePersonNameException extends LedgerException {

    private final String name;

    public DuplicatePersonNameException(String name) {
        super("An open person called \"%s\" already exists".formatted(name));
        this.name = name;
    }

    public String personName() {
        return name;
    }
}

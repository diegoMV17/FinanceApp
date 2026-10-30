package com.personalfinance.domain.ledger;

/**
 * Archiving twice would overwrite the date it happened on, which is a fact the
 * history should keep. Same reasoning as archiving an account twice.
 */
public final class PersonAlreadyArchivedException extends LedgerException {

    private final Person person;

    public PersonAlreadyArchivedException(Person person) {
        super("%s was already archived on %s"
                .formatted(person.name(), person.archivedAt().orElseThrow()));
        this.person = person;
    }

    public Person person() {
        return person;
    }
}

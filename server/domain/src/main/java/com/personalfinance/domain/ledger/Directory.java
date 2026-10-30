package com.personalfinance.domain.ledger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The directory of people: whoever you lend to, borrow from, or keep money for
 * (HU19, HU21).
 *
 * <p>This is the only place that hands out {@link Person} instances, for the
 * same reason {@link Chart} is the only one that hands out accounts: two
 * {@code Person} objects called "Juan" with different ids are two different
 * people, and his position would quietly split in half.
 *
 * <p>Nothing is ever removed. Archiving takes a person out of the lists you
 * pick from and leaves them in the ones you read history from.
 */
public final class Directory {

    private final Map<PersonId, Person> people = new LinkedHashMap<>();

    /**
     * Adds a person under the id the caller brought, which is how the phone can
     * write somebody down offline and send it afterwards.
     */
    public Person open(PersonId id, String name) {
        return add(Person.open(id, name));
    }

    /** Adds a person with an id of their own. The usual way. */
    public Person open(String name) {
        return open(PersonId.generate(), name);
    }

    /**
     * Registers a person built elsewhere, such as one loaded from the database.
     *
     * <p>An id already in the directory is refused rather than replaced: the
     * accounts already linked point at the instance that is there, and swapping
     * it would split that person in two.
     */
    public Person add(Person person) {
        Objects.requireNonNull(person, "person");
        if (people.containsKey(person.id())) {
            throw new DuplicatePersonException(person.id());
        }
        if (!person.isArchived()) {
            refuseDuplicateName(person.name());
        }
        people.put(person.id(), person);
        return person;
    }

    public void rename(PersonId id, String newName) {
        Person person = require(id);
        if (!person.answersTo(newName)) {
            refuseDuplicateName(newName);
        }
        person.rename(newName);
    }

    /**
     * Archives a person once nothing of theirs is open.
     *
     * <p>The catalogue is asked rather than told, for the same reason
     * {@link Chart#archive} asks the ledger: a person cannot be trusted to
     * report what is still open in their name, and a caller cannot be trusted
     * to supply it.
     *
     * <p>What it checks is that every account linked to them is archived — not
     * that their position is zero. An account that holds money already refuses
     * to archive with a balance, so this is the same promise arrived at through
     * the rule that owns it.
     */
    public Person archive(PersonId id, Chart chart, Instant when) {
        Objects.requireNonNull(chart, "chart");
        Person person = require(id);

        List<Account> stillOpen = chart.selectableOf(person);
        if (!stillOpen.isEmpty()) {
            throw new PersonStillHasOpenAccountsException(person, stillOpen);
        }

        person.archive(when);
        return person;
    }

    public Person require(PersonId id) {
        Person found = people.get(Objects.requireNonNull(id, "id"));
        if (found == null) {
            throw new UnknownPersonException(id);
        }
        return found;
    }

    public Optional<Person> find(PersonId id) {
        return Optional.ofNullable(people.get(Objects.requireNonNull(id, "id")));
    }

    /** Everyone ever written down, archived included. For history and reports. */
    public List<Person> all() {
        return List.copyOf(people.values());
    }

    /** Whoever can still be picked when linking an account. */
    public List<Person> selectable() {
        return people.values().stream().filter(person -> !person.isArchived()).toList();
    }

    public List<Person> archived() {
        return people.values().stream().filter(Person::isArchived).toList();
    }

    private void refuseDuplicateName(String name) {
        boolean taken = selectable().stream().anyMatch(person -> person.answersTo(name));
        if (taken) {
            throw new DuplicatePersonNameException(name.trim());
        }
    }
}

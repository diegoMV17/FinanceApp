package com.personalfinance.domain.ledger;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Somebody you owe, who owes you, or whose money you are holding (HU19).
 *
 * <p>This is only the identity. A person does not know their accounts and does
 * not know any balance: the link lives on the {@link Account}, which is also
 * where the database keeps it, and the arithmetic is {@link PersonPosition}.
 * A list of accounts here would be the same fact written twice, and one of the
 * two copies would eventually be wrong.
 *
 * <p>Archiving and renaming go through {@link Directory}, which is the only
 * thing that can see the whole directory and the catalogue of accounts at once.
 * A person cannot archive themselves, because deciding whether that is safe
 * needs to know what is still open in their name.
 */
public final class Person {

    /** The same box an account name fits in, measured the same way. */
    public static final int MAX_NAME_LENGTH = 60;

    private final PersonId id;
    private String name;
    private Instant archivedAt;

    private Person(PersonId id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireUsableName(name);
    }

    public static Person open(PersonId id, String name) {
        return new Person(id, name);
    }

    public static Person open(String name) {
        return new Person(PersonId.generate(), name);
    }

    /**
     * A person as they were stored.
     *
     * @param archivedAt when they were archived, or {@code null} if still open
     */
    public static Person rehydrated(PersonId id, String name, Instant archivedAt) {
        Person loaded = new Person(id, name);
        loaded.archivedAt = archivedAt;
        return loaded;
    }

    void rename(String newName) {
        this.name = requireUsableName(newName);
    }

    /**
     * Takes them out of the lists you pick from and leaves them in the ones you
     * read history from. Whether anything of theirs is still open is
     * {@link Directory#archive}; what this refuses is doing it twice, because
     * the second time would overwrite the date of the first.
     */
    void archive(Instant when) {
        if (isArchived()) {
            throw new PersonAlreadyArchivedException(this);
        }
        this.archivedAt = Objects.requireNonNull(when, "when");
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public PersonId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Optional<Instant> archivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    /** Two open people cannot share a name; an archived one may. */
    public boolean answersTo(String candidateName) {
        Objects.requireNonNull(candidateName, "candidateName");
        return name.equalsIgnoreCase(candidateName.trim());
    }

    /**
     * Whether this person is called exactly that, capitals included.
     *
     * <p>A different question from {@link #answersTo}, and the same distinction
     * {@link Account#isNamed} draws: ignoring case is right for deciding whether
     * two people would be indistinguishable in a selector, and wrong for
     * deciding whether a rename has anything to do.
     */
    public boolean isNamed(String candidateName) {
        return name.equals(requireUsableName(candidateName));
    }

    /**
     * Whether this is the same person somebody is describing again, used to tell
     * a retried "add this person" from a contradiction.
     *
     * <p>Only what the caller states counts: id and name, the name under the
     * same rule as {@link #answersTo}. Whether they have been archived since is
     * a fact the book added, not content.
     */
    public boolean sameContentAs(Person other) {
        Objects.requireNonNull(other, "other");
        return id.equals(other.id) && answersTo(other.name);
    }

    private static String requireUsableName(String candidate) {
        Objects.requireNonNull(candidate, "name");
        String trimmed = Text.within("name", candidate, MAX_NAME_LENGTH);
        if (trimmed.isEmpty()) {
            throw new BlankPersonNameException();
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Person person && id.equals(person.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return isArchived() ? name + " [archivada]" : name;
    }
}

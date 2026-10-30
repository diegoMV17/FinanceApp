package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A pocket of money. Never deleted, only archived: a report for March still has
 * to print the name of a category you stopped using in April.
 *
 * <p>Archiving, renaming and saying whose it is all go through {@link Chart},
 * which is the only thing that can see the whole catalogue and the book at
 * once. An account cannot archive itself, because deciding whether it is safe
 * to do so needs a balance, and a balance is not an account's to know.
 *
 * <p>Three of the seven kinds <em>are</em> somebody — what they owe you, what
 * you owe them, the money of theirs you hold — and those may carry a
 * {@link Person}. That link is what lets three accounts be read as one person
 * (HU19), and it is the only thing on an account the ledger never looks at.
 */
public final class Account {

    /** Long enough for "Bancolombia ahorros de la casa", short enough to fit a list. */
    public static final int MAX_NAME_LENGTH = 60;

    private final AccountId id;
    private final AccountType type;
    private String name;
    private Instant archivedAt;
    private Person person;

    private Account(AccountId id, AccountType type, String name, Person person) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.name = requireUsableName(name);
        this.person = requireMayBelongToAPerson(person);
    }

    public static Account open(AccountId id, AccountType type, String name) {
        return new Account(id, type, name, null);
    }

    public static Account open(AccountType type, String name) {
        return new Account(AccountId.generate(), type, name, null);
    }

    /**
     * An account as it was stored. Unlike {@link #archive}, it does not ask for
     * a balance: that rule was checked on the day the account was archived, and
     * loading history must not re-litigate it.
     *
     * <p>What it does still apply is the rule about who an account may belong
     * to, because that one is an invariant and not a decision taken on a date:
     * a stored category with a person attached is a row somebody wrote around
     * the CHECK, and the right response is to fail on load.
     *
     * @param archivedAt when it was archived, or {@code null} if it is still open
     * @param person     the person it belongs to, or {@code null}; it has to be
     *                   the very instance the {@link Directory} handed out
     */
    public static Account rehydrated(
            AccountId id, AccountType type, String name, Instant archivedAt, Person person) {
        Account loaded = new Account(id, type, name, person);
        loaded.archivedAt = archivedAt;
        return loaded;
    }

    void rename(String newName) {
        this.name = requireUsableName(newName);
    }

    /**
     * HU19. Says whose account this is, so three accounts can be read as one
     * person.
     *
     * <p>Moving the link is allowed and clearing it is not representable: there
     * is no method for it. Attaching the wrong person is a slip worth
     * correcting, and the ledger does not move either way — only the grouping
     * does. Going back to belonging to nobody, on the other hand, would erase a
     * stated fact in a book where nothing is ever erased.
     */
    void linkTo(Person newPerson) {
        Objects.requireNonNull(newPerson, "person");
        if (!type.belongsToAPerson()) {
            throw new AccountCannotBelongToAPersonException(this);
        }
        if (isArchived()) {
            throw new ArchivedAccountException(this);
        }
        if (newPerson.isArchived()) {
            throw new ArchivedPersonException(newPerson);
        }
        this.person = newPerson;
    }

    /**
     * Money-holding accounts must be empty first: archiving a wallet that still
     * has 50,000 in it would simply lose the money. Categories carry no money,
     * so they archive with their history intact.
     *
     * @param remainingBalance what the book says is left, scheduled movements included
     */
    void archive(Money remainingBalance, Instant archivedAt) {
        Objects.requireNonNull(remainingBalance, "remainingBalance");
        if (isArchived()) {
            throw new AccountAlreadyArchivedException(this);
        }
        if (type.requiresZeroBalanceToArchive() && !remainingBalance.isZero()) {
            throw new AccountStillHoldsMoneyException(this, remainingBalance);
        }
        this.archivedAt = Objects.requireNonNull(archivedAt, "archivedAt");
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** The balance as the person should read it, with the sign convention applied. */
    public Money presentationOf(Money ledgerBalance) {
        return ledgerBalance.times(type.nature().presentationSign());
    }

    public AccountId id() {
        return id;
    }

    public AccountType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public Optional<Instant> archivedAt() {
        return Optional.ofNullable(archivedAt);
    }

    /** Whose account this is, if it is anybody's. */
    public Optional<Person> person() {
        return Optional.ofNullable(person);
    }

    public boolean belongsTo(Person candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return candidate.equals(person);
    }

    /** Two open accounts of one type cannot share a name; an archived one may. */
    public boolean answersTo(String candidateName) {
        Objects.requireNonNull(candidateName, "candidateName");
        return name.equalsIgnoreCase(candidateName.trim());
    }

    /**
     * Whether this account is called exactly that, capitals included. Only the
     * blanks around the candidate are ignored, because those never make it
     * into a name anyway.
     *
     * <p>A different question from {@link #answersTo}, and mixing the two
     * loses a rename: "nequi" and "Nequi" are the same name for deciding
     * whether two accounts would be indistinguishable in a selector, and
     * different names for deciding whether a rename has anything to do.
     */
    public boolean isNamed(String candidateName) {
        return name.equals(requireUsableName(candidateName));
    }

    /**
     * Whether this is the same account somebody is describing again, used to
     * tell a retried "open this account" from a contradiction.
     *
     * <p>Only what the caller states counts: id, type and name, and the name
     * under the same rule as {@link #answersTo}. Whether it has been archived
     * since, and whose it has become, are facts the book added, not content:
     * opening an account never states a person, and linking one is its own
     * operation.
     */
    public boolean sameContentAs(Account other) {
        Objects.requireNonNull(other, "other");
        return id.equals(other.id) && type == other.type && answersTo(other.name);
    }

    /**
     * Only the three kinds that <em>are</em> somebody. Checked in the
     * constructor so that {@code rehydrated} is held to it too.
     *
     * <p>{@code this} is handed to the exception from inside the constructor on
     * purpose: by this point the id, the type and the name are set, which is all
     * the message needs, and reporting which account it was beats a message that
     * only names the rule.
     */
    private Person requireMayBelongToAPerson(Person candidate) {
        if (candidate != null && !type.belongsToAPerson()) {
            throw new AccountCannotBelongToAPersonException(this);
        }
        return candidate;
    }

    private static String requireUsableName(String candidate) {
        Objects.requireNonNull(candidate, "name");
        String trimmed = Text.within("name", candidate, MAX_NAME_LENGTH);
        if (trimmed.isEmpty()) {
            throw new BlankAccountNameException();
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Account account && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        String state = isArchived() ? " [archivada]" : "";
        String whose = person == null ? "" : " de " + person.name();
        return "%s (%s)%s%s".formatted(name, type, whose, state);
    }
}

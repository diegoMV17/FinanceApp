package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A pocket of money. Never deleted, only archived: a report for March still has
 * to print the name of a category you stopped using in April.
 *
 * <p>Archiving and renaming go through {@link Chart}, which is the only thing
 * that can see the whole catalogue and the book at once. An account cannot
 * archive itself, because deciding whether it is safe to do so needs a balance,
 * and a balance is not an account's to know.
 */
public final class Account {

    /** Long enough for "Bancolombia ahorros de la casa", short enough to fit a list. */
    public static final int MAX_NAME_LENGTH = 60;

    private final AccountId id;
    private final AccountType type;
    private String name;
    private Instant archivedAt;

    private Account(AccountId id, AccountType type, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.name = requireUsableName(name);
    }

    public static Account open(AccountId id, AccountType type, String name) {
        return new Account(id, type, name);
    }

    public static Account open(AccountType type, String name) {
        return new Account(AccountId.generate(), type, name);
    }

    /**
     * An account as it was stored. Unlike {@link #archive}, it does not ask for
     * a balance: that rule was checked on the day the account was archived, and
     * loading history must not re-litigate it.
     *
     * @param archivedAt when it was archived, or {@code null} if it is still open
     */
    public static Account rehydrated(
            AccountId id, AccountType type, String name, Instant archivedAt) {
        Account loaded = new Account(id, type, name);
        loaded.archivedAt = archivedAt;
        return loaded;
    }

    void rename(String newName) {
        this.name = requireUsableName(newName);
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
     * since is a fact the book added, not content.
     */
    public boolean sameContentAs(Account other) {
        Objects.requireNonNull(other, "other");
        return id.equals(other.id) && type == other.type && answersTo(other.name);
    }

    private static String requireUsableName(String candidate) {
        Objects.requireNonNull(candidate, "name");
        String trimmed = Text.within("name", candidate, MAX_NAME_LENGTH);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("An account needs a name");
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
        return "%s (%s)%s".formatted(name, type, state);
    }
}

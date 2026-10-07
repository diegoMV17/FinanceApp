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
    boolean answersTo(String candidateName) {
        return name.equalsIgnoreCase(candidateName.trim());
    }

    private static String requireUsableName(String candidate) {
        Objects.requireNonNull(candidate, "name");
        String trimmed = candidate.trim();
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

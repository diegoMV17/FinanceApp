package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A pocket of money. Never deleted, only archived: a report for March still has
 * to print the name of a category you stopped using in April.
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

    public void rename(String newName) {
        this.name = requireUsableName(newName);
    }

    /**
     * Archives the account. Money-holding accounts must be empty first:
     * archiving a wallet that still has 50,000 in it would simply lose the money.
     */
    public void archive(Money currentBalance, Instant archivedAt) {
        Objects.requireNonNull(currentBalance, "currentBalance");
        if (type.requiresZeroBalanceToArchive() && !currentBalance.isZero()) {
            throw new AccountStillHoldsMoneyException(this, currentBalance);
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
        return "%s (%s)".formatted(name, type);
    }
}

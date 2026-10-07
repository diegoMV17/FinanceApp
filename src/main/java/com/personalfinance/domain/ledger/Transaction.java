package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One financial event, made of entries that sum to zero.
 *
 * <p>A transaction carries no amount of its own. If it did, that amount could
 * drift away from the sum of its entries, and then there would be two answers
 * to the same question.
 *
 * <p>Nothing here can be modified after construction. Correcting a transaction
 * means cancelling it and recording a replacement, which arrives with Block C.
 */
public final class Transaction {

    private static final int MINIMUM_ENTRIES = 2;

    private final TransactionId id;
    private final LocalDate date;
    private final String description;
    private final TransactionSource source;
    private final List<Entry> entries;

    private Transaction(
            TransactionId id,
            LocalDate date,
            String description,
            TransactionSource source,
            List<Entry> entries) {

        this.id = Objects.requireNonNull(id, "id");
        this.date = Objects.requireNonNull(date, "date");
        this.source = Objects.requireNonNull(source, "source");
        this.description = description == null ? "" : description.trim();
        this.entries = List.copyOf(requireBalancedEntries(entries));
    }

    public static Transaction record(
            TransactionId id,
            LocalDate date,
            String description,
            TransactionSource source,
            List<Entry> entries) {

        return new Transaction(id, date, description, source, entries);
    }

    public static Transaction record(LocalDate date, String description, List<Entry> entries) {
        return new Transaction(
                TransactionId.generate(), date, description, TransactionSource.APP, entries);
    }

    /** How much this transaction moved the given account. Zero if it did not touch it. */
    public Money effectOn(Account account) {
        Objects.requireNonNull(account, "account");
        return entries.stream()
                .filter(entry -> entry.touches(account))
                .map(Entry::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    public boolean touches(Account account) {
        return entries.stream().anyMatch(entry -> entry.touches(account));
    }

    public TransactionId id() {
        return id;
    }

    public LocalDate date() {
        return date;
    }

    public String description() {
        return description;
    }

    public TransactionSource source() {
        return source;
    }

    public List<Entry> entries() {
        return entries;
    }

    private static List<Entry> requireBalancedEntries(List<Entry> candidates) {
        Objects.requireNonNull(candidates, "entries");

        if (candidates.size() < MINIMUM_ENTRIES) {
            throw new NotEnoughEntriesException(candidates.size(), MINIMUM_ENTRIES);
        }

        Money total = Money.sum(candidates.stream().map(Entry::amount).toList());
        if (!total.isZero()) {
            throw new UnbalancedTransactionException(total);
        }

        return candidates;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Transaction transaction && id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "%s %s %s".formatted(date, description, entries);
    }
}

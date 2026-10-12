package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One financial event, made of entries that sum to zero.
 *
 * <p>A transaction carries no amount of its own. If it did, that amount could
 * drift away from the sum of its entries, and then there would be two answers
 * to the same question.
 *
 * <p>The entries never change. Correcting a transaction does not edit it: the
 * old one is cancelled and a new one takes its place, pointing back at what it
 * replaced. That chain is the audit trail, and it is why a balance can always
 * be explained by the movements that produced it.
 *
 * <p>The one thing that does change after construction is the cancellation,
 * and it can only be set once.
 */
public final class Transaction {

    private static final int MINIMUM_ENTRIES = 2;

    /** Room for a sentence, not for a document pasted in by accident. */
    public static final int MAX_DESCRIPTION_LENGTH = 200;

    private final TransactionId id;
    private final LocalDate date;
    private final String description;
    private final TransactionSource source;
    private final List<Entry> entries;
    private final TransactionId replaces;

    private Cancellation cancellation;

    private Transaction(
            TransactionId id,
            LocalDate date,
            String description,
            TransactionSource source,
            List<Entry> entries,
            TransactionId replaces) {

        this.id = Objects.requireNonNull(id, "id");
        this.date = Objects.requireNonNull(date, "date");
        this.source = Objects.requireNonNull(source, "source");
        this.description = Text.within("description", description, MAX_DESCRIPTION_LENGTH);
        this.entries = List.copyOf(requireBalancedEntries(entries));
        this.replaces = replaces;
    }

    /**
     * A new movement. The id comes from the caller — the phone generates it
     * offline — which is what lets a retried request be recognised as the same
     * movement instead of recorded twice.
     */
    public static Transaction record(
            TransactionId id,
            LocalDate date,
            String description,
            TransactionSource source,
            List<Entry> entries) {

        return new Transaction(id, date, description, source, entries, null);
    }

    /**
     * A transaction as it was stored, cancellation included. The balance rules
     * still run: a stored transaction that does not add up is a corrupt book,
     * and loading it must fail rather than carry the error into every balance.
     *
     * @param replaces     the transaction this one corrects, or {@code null}
     * @param cancellation how it was cancelled, or {@code null} if it still counts
     */
    public static Transaction rehydrated(
            TransactionId id,
            LocalDate date,
            String description,
            TransactionSource source,
            List<Entry> entries,
            TransactionId replaces,
            Cancellation cancellation) {

        Transaction loaded = new Transaction(id, date, description, source, entries, replaces);
        loaded.cancellation = cancellation;
        return loaded;
    }

    /**
     * A copy of this transaction marked as the successor of {@code original}.
     * The ledger uses it to carry out an amendment; it is not something a
     * caller assembles by hand.
     *
     * <p>It keeps this transaction's own id. Minting a new one here would mean
     * that sending the same correction twice produces two different
     * corrections, and nothing upstream could tell that the second one was a
     * retry of the first.
     */
    Transaction asReplacementFor(TransactionId original) {
        Objects.requireNonNull(original, "original");
        return new Transaction(id, date, description, source, entries, original);
    }

    /**
     * Whether this is the same movement somebody is describing again, used to
     * tell a retried request from a contradiction.
     *
     * <p>{@link #equals} cannot answer that: two transactions are equal when
     * their ids match, and that is precisely the case under suspicion. What is
     * compared here is everything the caller stated — id, date, description,
     * source and the entries in order.
     *
     * <p>What happened to the transaction afterwards is not content: a
     * cancellation and a {@code replaces} are facts the book added, and whoever
     * asks has to look at those separately.
     */
    public boolean sameContentAs(Transaction other) {
        Objects.requireNonNull(other, "other");
        return id.equals(other.id)
                && date.equals(other.date)
                && description.equals(other.description)
                && source == other.source
                && entries.equals(other.entries);
    }

    void cancel(Cancellation how) {
        Objects.requireNonNull(how, "how");
        if (isCancelled()) {
            throw new TransactionAlreadyCancelledException(this);
        }
        this.cancellation = how;
    }

    public boolean isCancelled() {
        return cancellation != null;
    }

    public Optional<Cancellation> cancellation() {
        return Optional.ofNullable(cancellation);
    }

    /** The transaction this one corrects, when it is a correction. */
    public Optional<TransactionId> replaces() {
        return Optional.ofNullable(replaces);
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
        String state = isCancelled() ? " [anulada]" : "";
        return "%s %s %s%s".formatted(date, description, entries, state);
    }
}

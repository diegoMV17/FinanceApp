package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The book of transactions, and the answers you can read off it.
 *
 * <p>No balance is ever stored. Every number here is derived from the entries,
 * so a balance cannot drift out of step with the movements that produced it.
 *
 * <p>Nothing is ever removed either. Cancelling stops a transaction from
 * counting; it does not erase it. Every balance therefore has a history behind
 * it that explains how it got there.
 *
 * <p>Every query takes the date to answer as of. The domain has no clock of its
 * own on purpose: a method that silently reads "now" is a method you cannot
 * test for last month.
 */
public final class Ledger {

    private final Map<TransactionId, Transaction> book = new LinkedHashMap<>();

    public void record(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (book.containsKey(transaction.id())) {
            throw new DuplicateTransactionException(transaction.id());
        }
        book.put(transaction.id(), transaction);
    }

    /** HU07. The transaction stops counting but stays in the history. */
    public Transaction cancel(TransactionId id, Instant when, String reason) {
        Transaction target = require(id);
        target.cancel(Cancellation.because(when, reason));
        return target;
    }

    /**
     * HU06. Correcting a movement: the old version is cancelled and the
     * correction is recorded in its place, pointing back at what it replaced.
     *
     * <p>There is no in-place edit anywhere in this design. An amended
     * 35,000 lunch leaves two rows behind, not one changed row, and the balance
     * only ever reflects the surviving version.
     *
     * @param correction a freshly built transaction, usually from {@link Operations}
     * @return the recorded replacement, which has its own id
     */
    public Transaction amend(TransactionId originalId, Transaction correction, Instant when) {
        Objects.requireNonNull(correction, "correction");
        Transaction original = require(originalId);

        Transaction replacement = correction.asReplacementFor(originalId);
        original.cancel(Cancellation.supersededBy(when, replacement.id()));
        record(replacement);
        return replacement;
    }

    /** The raw ledger balance, signs and all. Mostly useful to the ledger itself. */
    public Money balanceOf(Account account, LocalDate asOf) {
        Objects.requireNonNull(account, "account");
        return countedUpTo(asOf)
                .map(transaction -> transaction.effectOn(account))
                .reduce(Money.ZERO, Money::plus);
    }

    /** The balance as the person reads it: a fund of 70,000 shows as 70,000. */
    public Money displayedBalanceOf(Account account, LocalDate asOf) {
        return account.presentationOf(balanceOf(account, asOf));
    }

    /**
     * Everything of one kind, as the person reads it: total expenses, total
     * owed to you, total held for other people.
     */
    public Money totalFor(AccountType type, LocalDate asOf) {
        Objects.requireNonNull(type, "type");
        Money onTheLedger = countedUpTo(asOf)
                .flatMap(transaction -> transaction.entries().stream())
                .filter(entry -> entry.account().type() == type)
                .map(Entry::amount)
                .reduce(Money.ZERO, Money::plus);

        return onTheLedger.times(type.nature().presentationSign());
    }

    /**
     * What is left once you take out the money that only passes through you.
     * Your bank says 500,000, but 100,000 of it is your mother's.
     */
    public Money moneyReallyMine(LocalDate asOf) {
        return totalFor(AccountType.ASSET, asOf)
                .minus(totalFor(AccountType.THIRD_PARTY_FUND, asOf))
                .minus(totalFor(AccountType.PAYABLE, asOf));
    }

    /**
     * The invariant that proves the book is sound: every entry ever written,
     * cancelled ones included, added up, is zero. Nothing unbalanced was ever
     * written, whatever happened to it afterwards.
     */
    public Money sumOfEveryEntry() {
        return book.values().stream()
                .flatMap(transaction -> transaction.entries().stream())
                .map(Entry::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    /** Everything ever recorded, cancelled included, oldest first. */
    public List<Transaction> history() {
        return List.copyOf(book.values());
    }

    /** What still counts, oldest first. */
    public List<Transaction> activeTransactions() {
        return book.values().stream().filter(transaction -> !transaction.isCancelled()).toList();
    }

    /** HU21, HU24: how a fund, a person or an account has moved, oldest first. */
    public List<Transaction> movementsOf(Account account) {
        return activeTransactions().stream()
                .filter(transaction -> transaction.touches(account))
                .toList();
    }

    /**
     * The full correction chain for a movement, oldest version first: the
     * original, then whatever replaced it, and so on. This is the answer to
     * "what did this used to say?".
     */
    public List<Transaction> revisionsOf(TransactionId id) {
        List<Transaction> chain = new ArrayList<>();
        chain.add(require(id));

        TransactionId current = id;
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Transaction candidate : book.values()) {
                if (candidate.replaces().filter(current::equals).isPresent()) {
                    chain.add(candidate);
                    current = candidate.id();
                    grew = true;
                    break;
                }
            }
        }
        return List.copyOf(chain);
    }

    private Transaction require(TransactionId id) {
        Transaction found = book.get(Objects.requireNonNull(id, "id"));
        if (found == null) {
            throw new UnknownTransactionException(id);
        }
        return found;
    }

    private Stream<Transaction> countedUpTo(LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf");
        return book.values().stream()
                .filter(transaction -> !transaction.isCancelled())
                .filter(transaction -> !transaction.date().isAfter(asOf));
    }
}

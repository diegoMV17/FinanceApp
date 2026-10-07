package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The book of transactions, and the answers you can read off it.
 *
 * <p>No balance is ever stored. Every number here is derived from the entries,
 * so a balance cannot drift out of step with the movements that produced it.
 *
 * <p>Every query takes the date to answer as of. The domain has no clock of its
 * own on purpose: a method that silently reads "now" is a method you cannot
 * test for last month.
 */
public final class Ledger {

    private final List<Transaction> transactions = new ArrayList<>();

    public void record(Transaction transaction) {
        transactions.add(Objects.requireNonNull(transaction, "transaction"));
    }

    /** The raw ledger balance, signs and all. Mostly useful to the ledger itself. */
    public Money balanceOf(Account account, LocalDate asOf) {
        Objects.requireNonNull(account, "account");
        return upTo(asOf)
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
        Money onTheLedger = upTo(asOf)
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
     * added up, is zero. Block D leans on this.
     */
    public Money sumOfEveryEntry() {
        return transactions.stream()
                .flatMap(transaction -> transaction.entries().stream())
                .map(Entry::amount)
                .reduce(Money.ZERO, Money::plus);
    }

    public List<Transaction> transactions() {
        return List.copyOf(transactions);
    }

    /** HU21: how a given fund, person or account has moved, oldest first. */
    public List<Transaction> movementsOf(Account account) {
        return transactions.stream()
                .filter(transaction -> transaction.touches(account))
                .toList();
    }

    private java.util.stream.Stream<Transaction> upTo(LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf");
        return transactions.stream()
                .filter(transaction -> !transaction.date().isAfter(asOf));
    }
}

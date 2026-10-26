package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * HU25. A bounded question put to the book: which movements count.
 *
 * <p>Until this existed the book only knew one filter, "everything up to this
 * date", counted from the beginning of time. That is enough to answer what a
 * balance is and nothing else: not what was spent in March, not what went to
 * food, not what a report of one month looks like.
 *
 * <p>It lives in the domain and not in the read layer because HU25 asks for the
 * same filters over the movements <em>and</em> over the totals. A predicate
 * written beside the reads would be the same rule in two places, and sooner or
 * later the list and the total under it would disagree — which is the worst
 * kind of wrong, because both halves look reasonable on their own.
 *
 * <p>Every field is optional, and absent means no restriction. The one that is
 * not an id is {@code person}, and that is deliberate: an {@link Entry} holds
 * its {@link Account} and an account holds its {@link Person}, so the book can
 * answer "does this movement touch an account of Juan" without being handed the
 * chart. Resolving the person to a list of account ids out here would turn a
 * person with no accounts into an empty list, an empty list into "no filter",
 * and "no filter" into every movement in the book.
 *
 * @param accounts   every one of them must be touched, not any of them
 * @param minAmount  compared against {@link Transaction#magnitude()}
 */
public record MovementQuery(
        LocalDate from,
        LocalDate to,
        AccountType type,
        List<AccountId> accounts,
        Person person,
        Money minAmount,
        Money maxAmount) {

    public MovementQuery {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);

        requireWithinTheCentury(from);
        requireWithinTheCentury(to);

        if (from != null && to != null && from.isAfter(to)) {
            throw ReversedRangeException.ofDates(from, to);
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw ReversedRangeException.ofAmounts(minAmount, maxAmount);
        }
    }

    /** No restriction at all: the whole book. */
    public static MovementQuery everything() {
        return new MovementQuery(null, null, null, List.of(), null, null, null);
    }

    /**
     * Everything that had happened by that date, which is what every balance
     * is derived from.
     */
    public static MovementQuery upTo(LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf");
        return everything().between(null, asOf);
    }

    /** Both ends count as inside, and either one may be absent. */
    public MovementQuery between(LocalDate newFrom, LocalDate newTo) {
        return new MovementQuery(newFrom, newTo, type, accounts, person, minAmount, maxAmount);
    }

    public MovementQuery ofType(AccountType newType) {
        return new MovementQuery(from, to, newType, accounts, person, minAmount, maxAmount);
    }

    /** Adds one more account that the movement has to touch as well. */
    public MovementQuery touching(AccountId account) {
        Objects.requireNonNull(account, "account");

        List<AccountId> more = new ArrayList<>(accounts);
        more.add(account);

        return new MovementQuery(from, to, type, more, person, minAmount, maxAmount);
    }

    public MovementQuery withPerson(Person newPerson) {
        return new MovementQuery(from, to, type, accounts, newPerson, minAmount, maxAmount);
    }

    /** Both ends count as inside, measured against the movement magnitude. */
    public MovementQuery worth(Money newMinimum, Money newMaximum) {
        return new MovementQuery(from, to, type, accounts, person, newMinimum, newMaximum);
    }

    /** Whether that movement is one of the ones being asked about. */
    public boolean matches(Transaction movement) {
        Objects.requireNonNull(movement, "movement");

        return happenedInRange(movement.date())
                && touchesTheType(movement)
                && touchesEveryAccount(movement)
                && touchesThePerson(movement)
                && isWorthWhatWasAsked(movement.magnitude());
    }

    private boolean happenedInRange(LocalDate date) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    /**
     * A movement has no type of its own; it touches accounts that do. Asking
     * for EXPENSE therefore means "the movements that touch an expense
     * account", which is what makes it the same filter the totals use.
     */
    private boolean touchesTheType(Transaction movement) {
        return type == null
                || movement.entries().stream()
                        .anyMatch(entry -> entry.account().type() == type);
    }

    private boolean touchesEveryAccount(Transaction movement) {
        return accounts.stream().allMatch(movement::touches);
    }

    private boolean touchesThePerson(Transaction movement) {
        return person == null
                || movement.entries().stream()
                        .anyMatch(entry -> entry.account().belongsTo(person));
    }

    private boolean isWorthWhatWasAsked(Money magnitude) {
        return (minAmount == null || magnitude.compareTo(minAmount) >= 0)
                && (maxAmount == null || magnitude.compareTo(maxAmount) <= 0);
    }

    private static void requireWithinTheCentury(LocalDate date) {
        if (date == null) {
            return;
        }
        if (date.isBefore(Transaction.EARLIEST_DATE) || date.isAfter(Transaction.LATEST_DATE)) {
            throw new DateOutOfRangeException(
                    date, Transaction.EARLIEST_DATE, Transaction.LATEST_DATE);
        }
    }
}

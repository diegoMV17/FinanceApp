package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;

/**
 * HU26. How much you mean to spend on one category in one month.
 *
 * <p>A budget is a fact, like everything else here. Setting a different limit
 * does not edit this one: it records another one that supersedes it and points
 * back at it, exactly as a correction does to a movement. What you thought you
 * would spend in March is worth keeping — it is how you find out afterwards
 * that the limit was raised twice before the month was over.
 *
 * <p>It carries no spending of its own. What has been spent is derived from the
 * entries, like every other number in this book, and the two meet in
 * {@link BudgetProgress}. A stored "spent so far" would be a second truth that
 * drifts the first time a movement is cancelled.
 *
 * <p>The one thing that changes after construction is the supersession, and it
 * can only be set once.
 */
public final class Budget {

    private final BudgetId id;
    private final Account category;
    private final YearMonth month;
    private final Money limit;
    private final BudgetId replaces;

    private Instant supersededAt;

    private Budget(
            BudgetId id,
            Account category,
            YearMonth month,
            Money limit,
            BudgetId replaces,
            Instant supersededAt) {

        this.id = Objects.requireNonNull(id, "id");
        this.category = requireACategory(category);
        this.month = requireMonthInRange(month);
        this.limit = requireASensibleLimit(category, limit);
        this.replaces = replaces;
        this.supersededAt = supersededAt;
    }

    /**
     * A limit that nothing came before.
     *
     * <p>Public because the border builds one just to compare it: that is how
     * a retry is told apart from a contradiction without writing anything.
     */
    public static Budget of(BudgetId id, Account category, YearMonth month, Money limit) {
        return new Budget(id, category, month, limit, null, null);
    }

    /**
     * What the stored rows say, with every invariant applied again.
     *
     * <p>A row written past the constraints blows up here, loudly, the same way
     * an unbalanced transaction does. The alternative is a budget on an income
     * account quietly producing a percentage of something that was never spent.
     */
    public static Budget rehydrated(
            BudgetId id,
            Account category,
            YearMonth month,
            Money limit,
            BudgetId replaces,
            Instant supersededAt) {

        return new Budget(id, category, month, limit, replaces, supersededAt);
    }

    /** The replacement for a limit that was already set, with its own id. */
    static Budget replacing(Budget previous, BudgetId id, Money limit) {
        return new Budget(id, previous.category, previous.month, limit, previous.id, null);
    }

    /**
     * Stops this limit from counting. Once, and never undone.
     *
     * <p>It does not record <em>which</em> limit took its place, and that is
     * deliberate: the replacement already points back here through
     * {@link #replaces()}, and writing the same link in both directions is the
     * same fact stored twice. One of the two copies always ends up wrong. The
     * ledger settles a correction the same way.
     */
    void supersede(Instant when) {
        Objects.requireNonNull(when, "when");

        if (supersededAt != null) {
            throw new BudgetAlreadySupersededException(id, supersededAt);
        }
        this.supersededAt = when;
    }

    /**
     * Only what the caller states counts: the id, the category, the month and
     * the limit. That it was later superseded is something the book added, not
     * something anybody asked for, so it is not content.
     */
    public boolean sameContentAs(Budget other) {
        Objects.requireNonNull(other, "other");
        return id.equals(other.id)
                && category.id().equals(other.category.id())
                && month.equals(other.month)
                && limit.equals(other.limit);
    }

    /** HU27. How this month is going, given what has actually been spent. */
    public BudgetProgress progressWith(Money spent) {
        return new BudgetProgress(
                id, category.id(), category.name(), month, limit, spent);
    }

    public boolean isFor(Account otherCategory, YearMonth otherMonth) {
        return category.id().equals(otherCategory.id()) && month.equals(otherMonth);
    }

    public boolean hasLimit(Money candidate) {
        return limit.equals(candidate);
    }

    public boolean isLive() {
        return supersededAt == null;
    }

    public BudgetId id() {
        return id;
    }

    public Account category() {
        return category;
    }

    public YearMonth month() {
        return month;
    }

    public Money limit() {
        return limit;
    }

    /** The limit this one took the place of, when it took anybody's. */
    public Optional<BudgetId> replaces() {
        return Optional.ofNullable(replaces);
    }

    public Optional<Instant> supersededAt() {
        return Optional.ofNullable(supersededAt);
    }

    /**
     * An expense category and nothing else, checked in the constructor so that
     * {@link #rehydrated} applies it too. A category never changes type — the
     * schema refuses it — so a budget that was valid when it was written stays
     * valid for ever, which is what lets this live here.
     *
     * <p>A budget on a bank account would be a limit on how much money may sit
     * in the bank, and one on a receivable a limit on what somebody owes you.
     * Neither is a thing anybody wants, and both would produce a percentage of
     * something that was never spent.
     */
    private static Account requireACategory(Account candidate) {
        Objects.requireNonNull(candidate, "category");
        if (candidate.type() != AccountType.EXPENSE) {
            throw new UnexpectedAccountTypeException(candidate, AccountType.EXPENSE);
        }
        return candidate;
    }

    /**
     * The same century as a movement, for the same reason: a budget for the
     * year 999999999 would sit at the end of every month-by-month report for
     * the rest of the book's life, and nothing here is ever deleted.
     */
    private static YearMonth requireMonthInRange(YearMonth candidate) {
        Objects.requireNonNull(candidate, "month");

        Transaction.requireDateInRange(candidate.atDay(1));
        return candidate;
    }

    /**
     * Zero is not a budget of zero, it is the absence of a budget, and the two
     * read very differently in a report. The ceiling is the one an entry has,
     * for the arithmetic reason an entry has it.
     */
    private static Money requireASensibleLimit(Account category, Money candidate) {
        Objects.requireNonNull(candidate, "limit");

        if (!candidate.isPositive()) {
            throw new NonPositiveAmountException("A budget", candidate);
        }
        if (Math.abs(candidate.cents()) > Entry.MAX_ABSOLUTE_CENTS) {
            throw new AmountOutOfRangeException(
                    category, candidate, Entry.MAX_ABSOLUTE_CENTS);
        }
        return candidate;
    }

    @Override
    public String toString() {
        return "Budget[%s %s]".formatted(category.name(), month);
    }
}

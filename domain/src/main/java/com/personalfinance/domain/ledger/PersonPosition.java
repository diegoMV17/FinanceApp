package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * HU19, HU21. How you and one person stand, on a given date.
 *
 * <p>This is the number the whole application exists to produce. "How do Juan
 * and I stand?" is three balances read at once — what he owes you, what you owe
 * him, and how much of his money you are holding — and no account on its own
 * can answer it.
 *
 * <p>Nothing is stored. Every figure is derived from the entries, like every
 * other balance in this domain, and every figure comes out with the sign the
 * person reads: a fund holding 50,000 reads 50,000, not -50,000. The conversion
 * is {@link BalanceNature}, applied once, by the ledger.
 *
 * <p>The fields are flat rather than a {@link Person} plus three amounts, the
 * same way {@link com.personalfinance.domain.ledger.Account} is flattened on
 * its way out of a read: what leaves here is heading for a screen, and a person
 * carries mutators that only the aggregate should be able to call.
 *
 * @param archivedAt when the person was archived, or {@code null} if still open
 */
public record PersonPosition(
        PersonId id,
        String name,
        Instant archivedAt,
        Money owedToYou,
        Money youOwe,
        Money theirFund) {

    public PersonPosition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(owedToYou, "owedToYou");
        Objects.requireNonNull(youOwe, "youOwe");
        Objects.requireNonNull(theirFund, "theirFund");
    }

    /**
     * Works out the three sides from the book itself.
     *
     * <p>Archived accounts are counted. A report for March still has to print
     * what Juan owed in March, and an account of his archived in April was
     * perfectly real then.
     *
     * @param asOf the date to answer as of; the domain reads no clock
     */
    public static PersonPosition of(Person person, Chart chart, Ledger ledger, LocalDate asOf) {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(chart, "chart");
        Objects.requireNonNull(ledger, "ledger");

        List<Account> theirs = chart.allOf(person);

        return new PersonPosition(
                person.id(),
                person.name(),
                person.archivedAt().orElse(null),
                sideOf(theirs, AccountType.RECEIVABLE, ledger, asOf),
                sideOf(theirs, AccountType.PAYABLE, ledger, asOf),
                sideOf(theirs, AccountType.THIRD_PARTY_FUND, ledger, asOf));
    }

    /**
     * What would be left if everything with this person were settled today.
     *
     * <p>Their fund is subtracted for the same reason
     * {@link Ledger#moneyReallyMine} subtracts it: the money is in your pocket
     * but it is not yours, so it is not something they owe you.
     */
    public Money netOwedToYou() {
        return owedToYou.minus(youOwe).minus(theirFund);
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Optional<Instant> archivedOn() {
        return Optional.ofNullable(archivedAt);
    }

    private static Money sideOf(
            List<Account> theirs, AccountType type, Ledger ledger, LocalDate asOf) {

        return Money.sum(theirs.stream()
                .filter(account -> account.type() == type)
                .map(account -> ledger.displayedBalanceOf(account, asOf))
                .toList());
    }
}

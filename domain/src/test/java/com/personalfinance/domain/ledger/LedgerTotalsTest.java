package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Block K, second part: HU22 and HU23, the totals a dashboard is made of.
 *
 * <p>Up to a date gives a balance. Between two dates gives a period, which is
 * what a chart has on its horizontal axis, and the book could not answer it at
 * all until now.
 */
@DisplayName("K · the totals behind the dashboard")
class LedgerTotalsTest {

    private static final LocalDate MARCH_1 = LocalDate.of(2026, 3, 1);
    private static final LocalDate MARCH_31 = LocalDate.of(2026, 3, 31);
    private static final LocalDate APRIL_2 = LocalDate.of(2026, 4, 2);

    private final Chart chart = new Chart();
    private final Account nequi = chart.open(AccountType.ASSET, "Nequi");
    private final Account food = chart.open(AccountType.EXPENSE, "Alimentación");
    private final Account transport = chart.open(AccountType.EXPENSE, "Transporte");
    private final Account salary = chart.open(AccountType.INCOME, "Salario");

    private final Ledger ledger = new Ledger();

    @Test
    @DisplayName("K17 · a total over a range counts that range and nothing else")
    void aRangeCountsOnlyItself() {
        spend(MARCH_1, food, 30_000);
        spend(MARCH_31, transport, 5_000);
        spend(APRIL_2, food, 90_000);

        MovementQuery march = MovementQuery.everything().between(MARCH_1, MARCH_31);

        assertThat(ledger.totalFor(AccountType.EXPENSE, march))
                .as("both ends of March are inside, and April is not")
                .isEqualTo(Money.ofUnits(35_000));
        assertThat(ledger.totalFor(AccountType.EXPENSE, MovementQuery.everything()))
                .isEqualTo(Money.ofUnits(125_000));
    }

    /**
     * An expense is a debit and income is a credit, so on the ledger they carry
     * opposite signs. Both have to come out positive, because what a person
     * reads is "I spent 35,000" and "I earned 3,000,000", never one of the two
     * in negative.
     */
    @Test
    @DisplayName("K18 · a total comes out with the sign the person reads")
    void theSignIsTheOneYouRead() {
        spend(MARCH_1, food, 35_000);
        earn(MARCH_1, 3_000_000);

        MovementQuery march = MovementQuery.everything().between(MARCH_1, MARCH_31);

        assertThat(ledger.totalFor(AccountType.EXPENSE, march))
                .isEqualTo(Money.ofUnits(35_000));
        assertThat(ledger.totalFor(AccountType.INCOME, march))
                .isEqualTo(Money.ofUnits(3_000_000));
    }

    @Test
    @DisplayName("K19 · the breakdown by account adds up to the total of its type")
    void theBreakdownAddsUp() {
        spend(MARCH_1, food, 30_000);
        spend(MARCH_31, food, 20_000);
        spend(MARCH_31, transport, 5_000);

        MovementQuery march = MovementQuery.everything().between(MARCH_1, MARCH_31);

        assertThat(ledger.totalsByAccount(AccountType.EXPENSE, march))
                .containsEntry(food.id(), Money.ofUnits(50_000))
                .containsEntry(transport.id(), Money.ofUnits(5_000))
                .hasSize(2);
        assertThat(Money.sum(ledger.totalsByAccount(AccountType.EXPENSE, march).values()))
                .isEqualTo(ledger.totalFor(AccountType.EXPENSE, march));
    }

    @Test
    @DisplayName("K20 · without a type the breakdown covers every account that moved")
    void withoutATypeItIsEveryAccount() {
        spend(MARCH_1, food, 35_000);

        var everyAccount = ledger.totalsByAccount(null, MovementQuery.everything());

        assertThat(everyAccount)
                .containsEntry(food.id(), Money.ofUnits(35_000))
                .containsEntry(nequi.id(), Money.ofUnits(-35_000))
                .hasSize(2);
    }

    /**
     * An account that did not move is missing from the map on purpose. That it
     * exists and sits at zero is a fact about the chart, not about the entries,
     * and the chart is not here.
     */
    @Test
    @DisplayName("K21 · an account that did not move is absent, not zero")
    void anAccountThatDidNotMoveIsAbsent() {
        spend(MARCH_1, food, 35_000);

        assertThat(ledger.totalsByAccount(AccountType.EXPENSE, MovementQuery.everything()))
                .doesNotContainKey(transport.id());
    }

    @Test
    @DisplayName("K22 · a cancelled movement counts in no total")
    void cancelledCountsNowhere() {
        TransactionId lunch = spend(MARCH_1, food, 35_000);
        ledger.cancel(lunch, Instant.now(), "No fue");

        MovementQuery march = MovementQuery.everything().between(MARCH_1, MARCH_31);

        assertThat(ledger.totalFor(AccountType.EXPENSE, march)).isEqualTo(Money.ZERO);
        assertThat(ledger.totalsByAccount(AccountType.EXPENSE, march)).isEmpty();
    }

    @Test
    @DisplayName("K23 · a period with nothing in it totals zero, it does not fail")
    void anEmptyPeriodIsZero() {
        spend(APRIL_2, food, 90_000);

        MovementQuery march = MovementQuery.everything().between(MARCH_1, MARCH_31);

        assertThat(ledger.totalFor(AccountType.EXPENSE, march)).isEqualTo(Money.ZERO);
        assertThat(ledger.totalsByAccount(AccountType.EXPENSE, march)).isEmpty();
    }

    private TransactionId spend(LocalDate day, Account category, long units) {
        TransactionId id = TransactionId.generate();
        ledger.record(Operations.expense(
                id, day, "Gasto", nequi, category, Money.ofUnits(units)));
        return id;
    }

    private void earn(LocalDate day, long units) {
        ledger.record(Operations.income(TransactionId.generate(),
                day, "Sueldo", nequi, salary, Money.ofUnits(units)));
    }
}

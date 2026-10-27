package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block K, first part: HU25, asking the book a bounded question.
 *
 * <p>Until now the book only knew how to answer "everything up to this date".
 * A query lives in the domain rather than in the read layer on purpose: HU25
 * asks for the same filters over the movements <em>and</em> over the totals, so
 * a predicate written outside would be the same rule in two places, and one of
 * the two copies would end up wrong.
 */
@DisplayName("K · asking the book a bounded question")
class MovementQueryTest {

    private static final LocalDate MARCH_1 = LocalDate.of(2026, 3, 1);
    private static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);
    private static final LocalDate MARCH_31 = LocalDate.of(2026, 3, 31);

    private final Chart chart = new Chart();
    private final Account nequi = chart.open(AccountType.ASSET, "Nequi");
    private final Account cash = chart.open(AccountType.ASSET, "Efectivo");
    private final Account food = chart.open(AccountType.EXPENSE, "Alimentación");
    private final Account transport = chart.open(AccountType.EXPENSE, "Transporte");
    private final Account salary = chart.open(AccountType.INCOME, "Salario");
    private final Account owedByJuan = chart.open(AccountType.RECEIVABLE, "Juan");

    @Test
    @DisplayName("K1 · an empty query lets everything through")
    void everythingMatchesTheEmptyQuery() {
        assertThat(MovementQuery.everything().matches(lunchOn(MARCH_15))).isTrue();
    }

    @Nested
    @DisplayName("by date")
    class ByDate {

        @Test
        @DisplayName("K2 · both ends of the range count as inside it")
        void bothEndsAreInside() {
            MovementQuery march = MovementQuery.everything().between(MARCH_1, MARCH_31);

            assertThat(march.matches(lunchOn(MARCH_1))).isTrue();
            assertThat(march.matches(lunchOn(MARCH_31))).isTrue();
            assertThat(march.matches(lunchOn(MARCH_15))).isTrue();
        }

        @Test
        @DisplayName("K3 · a day outside the range is out")
        void outsideIsOut() {
            MovementQuery march = MovementQuery.everything().between(MARCH_1, MARCH_31);

            assertThat(march.matches(lunchOn(MARCH_1.minusDays(1)))).isFalse();
            assertThat(march.matches(lunchOn(MARCH_31.plusDays(1)))).isFalse();
        }

        @Test
        @DisplayName("K4 · one end on its own bounds only that side")
        void oneEndedRangesWork() {
            MovementQuery sinceTheFifteenth = MovementQuery.everything().between(MARCH_15, null);

            assertThat(sinceTheFifteenth.matches(lunchOn(MARCH_31))).isTrue();
            assertThat(sinceTheFifteenth.matches(lunchOn(MARCH_1))).isFalse();
            assertThat(MovementQuery.upTo(MARCH_15).matches(lunchOn(MARCH_1))).isTrue();
            assertThat(MovementQuery.upTo(MARCH_15).matches(lunchOn(MARCH_31))).isFalse();
        }
    }

    @Nested
    @DisplayName("by what it touches")
    class ByWhatItTouches {

        @Test
        @DisplayName("K5 · a type keeps the movements that touch an account of that type")
        void aTypePicksItsMovements() {
            MovementQuery expenses = MovementQuery.everything().ofType(AccountType.EXPENSE);

            assertThat(expenses.matches(lunchOn(MARCH_15))).isTrue();
            assertThat(expenses.matches(paydayOn(MARCH_15))).isFalse();
        }

        @Test
        @DisplayName("K6 · an account keeps the movements that touch it")
        void anAccountPicksItsMovements() {
            MovementQuery onFood = MovementQuery.everything().touching(food.id());

            assertThat(onFood.matches(lunchOn(MARCH_15))).isTrue();
            assertThat(onFood.matches(busOn(MARCH_15))).isFalse();
        }

        /**
         * The one that makes the filter worth having: what went from Nequi to
         * food. Two accounts mean AND, not OR, because with OR the answer
         * would be every meal plus everything that ever left Nequi, which is
         * not a question anybody asks.
         */
        @Test
        @DisplayName("K7 · two accounts mean both of them, not either of them")
        void twoAccountsMeanBoth() {
            MovementQuery fromNequiOnFood = MovementQuery.everything()
                    .touching(nequi.id())
                    .touching(food.id());

            assertThat(fromNequiOnFood.matches(lunchOn(MARCH_15)))
                    .as("the lunch was paid from Nequi")
                    .isTrue();
            assertThat(fromNequiOnFood.matches(busOn(MARCH_15)))
                    .as("also paid from Nequi, but it is not food")
                    .isFalse();
            assertThat(fromNequiOnFood.matches(cashLunchOn(MARCH_15)))
                    .as("food, but not from Nequi")
                    .isFalse();
        }

        @Test
        @DisplayName("K8 · a person keeps what touches any account of theirs")
        void aPersonPicksTheirMovements() {
            Person juan = Person.open("Juan");
            chart.linkToPerson(owedByJuan.id(), juan);

            MovementQuery withJuan = MovementQuery.everything().withPerson(juan);

            assertThat(withJuan.matches(lentToJuanOn(MARCH_15))).isTrue();
            assertThat(withJuan.matches(lunchOn(MARCH_15))).isFalse();
        }

        /**
         * The trap this filter is built to avoid. Resolving the person to a
         * list of account ids outside the book would turn a person with no
         * accounts into an empty list, an empty list into "no filter", and "no
         * filter" into <em>every movement in the book</em>, which is the
         * loudest possible wrong answer to a question about somebody.
         */
        @Test
        @DisplayName("K9 · a person with no accounts matches nothing, not everything")
        void aPersonWithoutAccountsMatchesNothing() {
            Person stranger = Person.open("Pedro");

            MovementQuery withPedro = MovementQuery.everything().withPerson(stranger);

            assertThat(withPedro.matches(lunchOn(MARCH_15))).isFalse();
            assertThat(withPedro.matches(lentToJuanOn(MARCH_15))).isFalse();
        }
    }

    @Nested
    @DisplayName("by value")
    class ByValue {

        @Test
        @DisplayName("K10 · the value of a movement is the sum of its positive entries")
        void theValueIsTheMagnitude() {
            assertThat(lunchOn(MARCH_15).magnitude()).isEqualTo(Money.ofUnits(35_000));
        }

        @Test
        @DisplayName("K11 · the range on the value includes both ends")
        void bothEndsOfTheValueAreInside() {
            MovementQuery between = MovementQuery.everything()
                    .worth(Money.ofUnits(35_000), Money.ofUnits(40_000));

            assertThat(between.matches(lunchOn(MARCH_15)))
                    .as("exactly the minimum is inside")
                    .isTrue();
            assertThat(between.matches(busOn(MARCH_15)))
                    .as("2,000 is below the minimum")
                    .isFalse();
        }

        @Test
        @DisplayName("K12 · one end on its own bounds only that side")
        void oneEndedValuesWork() {
            MovementQuery upToTenThousand =
                    MovementQuery.everything().worth(null, Money.ofUnits(10_000));

            assertThat(upToTenThousand.matches(busOn(MARCH_15))).isTrue();
            assertThat(upToTenThousand.matches(lunchOn(MARCH_15))).isFalse();
        }
    }

    @Nested
    @DisplayName("what it refuses to be")
    class WhatItRefusesToBe {

        @Test
        @DisplayName("K13 · a date outside the century of the book is refused")
        void aDateOutOfRangeIsRefused() {
            assertThatThrownBy(() ->
                    MovementQuery.everything().between(LocalDate.of(1999, 12, 31), MARCH_31))
                    .isInstanceOf(DateOutOfRangeException.class);

            assertThatThrownBy(() ->
                    MovementQuery.everything().between(MARCH_1, LocalDate.of(2100, 1, 1)))
                    .isInstanceOf(DateOutOfRangeException.class);
        }

        @Test
        @DisplayName("K14 · a reversed date range is refused, not quietly empty")
        void aReversedDateRangeIsRefused() {
            assertThatThrownBy(() -> MovementQuery.everything().between(MARCH_31, MARCH_1))
                    .isInstanceOf(ReversedRangeException.class);
        }

        @Test
        @DisplayName("K15 · a reversed value range is refused too")
        void aReversedValueRangeIsRefused() {
            assertThatThrownBy(() -> MovementQuery.everything()
                    .worth(Money.ofUnits(40_000), Money.ofUnits(10_000)))
                    .isInstanceOf(ReversedRangeException.class);
        }

        /**
         * A range whose two ends are the same day is one day, not a mistake.
         * It is how the question about one single day gets asked.
         */
        @Test
        @DisplayName("K16 · a single-day range is fine")
        void aSingleDayIsFine() {
            MovementQuery theFifteenth = MovementQuery.everything().between(MARCH_15, MARCH_15);

            assertThat(theFifteenth.matches(lunchOn(MARCH_15))).isTrue();
            assertThat(theFifteenth.matches(lunchOn(MARCH_1))).isFalse();
        }
    }

    private Transaction lunchOn(LocalDate day) {
        return Operations.expense(TransactionId.generate(),
                day, "Almuerzo", nequi, food, Money.ofUnits(35_000));
    }

    private Transaction cashLunchOn(LocalDate day) {
        return Operations.expense(TransactionId.generate(),
                day, "Almuerzo", cash, food, Money.ofUnits(35_000));
    }

    private Transaction busOn(LocalDate day) {
        return Operations.expense(TransactionId.generate(),
                day, "Bus", nequi, transport, Money.ofUnits(2_000));
    }

    private Transaction paydayOn(LocalDate day) {
        return Operations.income(TransactionId.generate(),
                day, "Sueldo", nequi, salary, Money.ofUnits(3_000_000));
    }

    private Transaction lentToJuanOn(LocalDate day) {
        return Operations.lend(TransactionId.generate(),
                day, "Prestamo", nequi, owedByJuan, Money.ofUnits(300_000));
    }
}

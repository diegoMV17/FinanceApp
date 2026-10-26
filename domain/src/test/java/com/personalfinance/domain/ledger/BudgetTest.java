package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block N: HU26 and HU27, a spending limit for a category and a month.
 *
 * <p>A budget is a fact, like everything else in this book: setting a different
 * limit does not edit the old one, it records a new one that supersedes it and
 * leaves the previous one in the history. What you thought you would spend in
 * March is itself worth keeping — it is how you find out that the limit was
 * raised three times before the month ended.
 */
@DisplayName("N · the spending limit of a category")
class BudgetTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final Instant WHEN = Instant.parse("2026-03-01T12:00:00Z");

    private final Chart chart = new Chart();
    private final Account food = chart.open(AccountType.EXPENSE, "Alimentación");
    private final Account transport = chart.open(AccountType.EXPENSE, "Transporte");
    private final Account nequi = chart.open(AccountType.ASSET, "Nequi");

    private final Budgets budgets = new Budgets();

    @Nested
    @DisplayName("what it refuses to be")
    class WhatItRefusesToBe {

        /**
         * A budget on Nequi would be a limit on how much money may sit in the
         * bank, which is not a thing anybody wants; and a budget on a
         * RECEIVABLE would be a limit on what somebody owes you. HU26 says
         * categories of expenses, and an expense category is the only account
         * where "spent against a limit" means something.
         */
        @Test
        @DisplayName("N1 · a budget belongs to an expense category and nothing else")
        void onlyExpenseCategories() {
            assertThatThrownBy(() -> budgets.set(
                    BudgetId.generate(), nequi, MARCH, Money.ofUnits(500_000), WHEN))
                    .isInstanceOf(UnexpectedAccountTypeException.class);
        }

        @Test
        @DisplayName("N2 · a budget on an archived category is refused")
        void notOnAnArchivedCategory() {
            chart.archive(transport.id(), new Ledger(), WHEN);

            assertThatThrownBy(() -> budgets.set(
                    BudgetId.generate(), transport, MARCH, Money.ofUnits(50_000), WHEN))
                    .isInstanceOf(ArchivedAccountException.class);
        }

        /**
         * A limit of zero is not a budget of zero, it is the absence of a
         * budget, and the two read very differently in a report.
         */
        @Test
        @DisplayName("N3 · a limit has to be positive")
        void theLimitIsPositive() {
            assertThatThrownBy(() -> budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ZERO, WHEN))
                    .isInstanceOf(NonPositiveAmountException.class);
            assertThatThrownBy(() -> budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ofUnits(-10), WHEN))
                    .isInstanceOf(NonPositiveAmountException.class);
        }

        @Test
        @DisplayName("N4 · a limit above what the book admits is refused")
        void theLimitHasACeiling() {
            assertThatThrownBy(() -> budgets.set(BudgetId.generate(), food, MARCH,
                    Money.ofCents(Entry.MAX_ABSOLUTE_CENTS + 1), WHEN))
                    .isInstanceOf(AmountOutOfRangeException.class);
        }

        @Test
        @DisplayName("N5 · a month outside the century of the book is refused")
        void theMonthIsInsideTheCentury() {
            assertThatThrownBy(() -> budgets.set(BudgetId.generate(), food,
                    YearMonth.of(1999, 12), Money.ofUnits(500_000), WHEN))
                    .isInstanceOf(DateOutOfRangeException.class);
            assertThatThrownBy(() -> budgets.set(BudgetId.generate(), food,
                    YearMonth.of(2100, 1), Money.ofUnits(500_000), WHEN))
                    .isInstanceOf(DateOutOfRangeException.class);
        }

        @Test
        @DisplayName("N6 · the same id twice is refused, whatever it says")
        void anIdIsUsedOnce() {
            BudgetId id = BudgetId.generate();
            budgets.set(id, food, MARCH, Money.ofUnits(500_000), WHEN);

            assertThatThrownBy(() -> budgets.set(id, transport, APRIL,
                    Money.ofUnits(90_000), WHEN))
                    .isInstanceOf(DuplicateBudgetException.class);
        }
    }

    @Nested
    @DisplayName("how a limit changes")
    class HowALimitChanges {

        @Test
        @DisplayName("N7 · a new limit supersedes the previous one and points back at it")
        void aNewLimitSupersedesTheOldOne() {
            Budget first = budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ofUnits(500_000), WHEN);
            Budget second = budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ofUnits(600_000), WHEN);

            assertThat(second.replaces()).contains(first.id());
            assertThat(second.limit()).isEqualTo(Money.ofUnits(600_000));
            assertThat(second.isLive()).isTrue();
            assertThat(second.category()).isSameAs(food);
            assertThat(second.month()).isEqualTo(MARCH);
        }

        /**
         * Nothing is deleted here either. The old limit keeps its row, with the
         * moment it stopped counting, so a report can still say what the plan
         * for March was before it was raised.
         */
        @Test
        @DisplayName("N8 · and the previous one stays in the history, marked")
        void theOldOneStaysInTheHistory() {
            Budget first = budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ofUnits(500_000), WHEN);
            Budget second = budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ofUnits(600_000), WHEN);

            assertThat(first.isLive()).isFalse();
            assertThat(first.supersededAt()).contains(WHEN);
            assertThat(first.supersededBy()).contains(second.id());
            assertThat(budgets.all()).containsExactly(first, second);
        }

        @Test
        @DisplayName("N9 · only one limit is live for a category and a month")
        void onlyOneIsLive() {
            budgets.set(BudgetId.generate(), food, MARCH, Money.ofUnits(500_000), WHEN);
            Budget second = budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ofUnits(600_000), WHEN);

            assertThat(budgets.liveFor(food, MARCH)).contains(second);
            assertThat(budgets.liveFor(MARCH)).containsExactly(second);
        }

        @Test
        @DisplayName("N10 · two categories in the same month do not collide")
        void twoCategoriesDoNotCollide() {
            Budget forFood = budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ofUnits(500_000), WHEN);
            Budget forTransport = budgets.set(
                    BudgetId.generate(), transport, MARCH, Money.ofUnits(90_000), WHEN);

            assertThat(forFood.replaces()).isEmpty();
            assertThat(forTransport.replaces()).isEmpty();
            assertThat(budgets.liveFor(MARCH)).containsExactly(forFood, forTransport);
        }

        @Test
        @DisplayName("N11 · the same category in two months does not collide either")
        void twoMonthsDoNotCollide() {
            Budget inMarch = budgets.set(
                    BudgetId.generate(), food, MARCH, Money.ofUnits(500_000), WHEN);
            Budget inApril = budgets.set(
                    BudgetId.generate(), food, APRIL, Money.ofUnits(600_000), WHEN);

            assertThat(inApril.replaces()).isEmpty();
            assertThat(budgets.liveFor(food, MARCH)).contains(inMarch);
            assertThat(budgets.liveFor(food, APRIL)).contains(inApril);
        }

        /**
         * Only what the caller states counts, which is the same rule as
         * everywhere else: what the book added afterwards — that it was
         * superseded, and by which one — is not content.
         */
        @Test
        @DisplayName("N12 · sameContentAs ignores what the book added afterwards")
        void sameContentIgnoresTheSupersession() {
            BudgetId id = BudgetId.generate();
            Budget first = budgets.set(id, food, MARCH, Money.ofUnits(500_000), WHEN);
            budgets.set(BudgetId.generate(), food, MARCH, Money.ofUnits(600_000), WHEN);

            assertThat(first.isLive()).isFalse();
            assertThat(first.sameContentAs(
                    Budget.of(id, food, MARCH, Money.ofUnits(500_000)))).isTrue();
            assertThat(first.sameContentAs(
                    Budget.of(id, food, MARCH, Money.ofUnits(600_000)))).isFalse();
        }
    }

    @Nested
    @DisplayName("how it is going")
    class HowItIsGoing {

        private final Budget budget = budgets.set(
                BudgetId.generate(), food, MARCH, Money.ofUnits(500_000), WHEN);

        @Test
        @DisplayName("N13 · the percentage used, and what is left")
        void thePercentageAndTheRemainder() {
            BudgetProgress half = budget.progressWith(Money.ofUnits(250_000));

            assertThat(half.percentageUsed()).isEqualTo(50);
            assertThat(half.remaining()).isEqualTo(Money.ofUnits(250_000));
            assertThat(half.isNearTheLimit()).isFalse();
            assertThat(half.isExceeded()).isFalse();
        }

        /**
         * HU27 asks for the two apart: getting close is a warning and going
         * over is a fact. The threshold lives here, in the domain, and travels
         * already decided to the edge — if the phone recomputed it, the server
         * and the phone would warn on different days.
         */
        @Test
        @DisplayName("N14 · near the limit and over it are two different things")
        void nearIsNotOver() {
            assertThat(budget.progressWith(Money.ofUnits(399_000)).isNearTheLimit())
                    .as("79%% todavía no es acercarse")
                    .isFalse();
            assertThat(budget.progressWith(Money.ofUnits(400_000)).isNearTheLimit())
                    .as("80%% sí")
                    .isTrue();
            assertThat(budget.progressWith(Money.ofUnits(400_000)).isExceeded()).isFalse();

            BudgetProgress over = budget.progressWith(Money.ofUnits(500_001));
            assertThat(over.isExceeded()).isTrue();
            assertThat(over.isNearTheLimit())
                    .as("pasarse no es acercarse: son dos avisos distintos")
                    .isFalse();
            assertThat(over.remaining()).isEqualTo(Money.ofUnits(-1));
        }

        @Test
        @DisplayName("N15 · exactly the limit is spent, not exceeded")
        void exactlyTheLimitIsNotOver() {
            BudgetProgress exact = budget.progressWith(Money.ofUnits(500_000));

            assertThat(exact.percentageUsed()).isEqualTo(100);
            assertThat(exact.isExceeded()).isFalse();
            assertThat(exact.isNearTheLimit()).isTrue();
            assertThat(exact.remaining()).isEqualTo(Money.ZERO);
        }

        /**
         * A percentage nobody can draw is no use, and the arithmetic has to
         * stay inside a long: a limit of one cent against a book-sized spend
         * would overflow a plain multiply by a hundred.
         */
        @Test
        @DisplayName("N16 · an absurd percentage is capped instead of overflowing")
        void anAbsurdPercentageIsCapped() {
            Budget tiny = budgets.set(
                    BudgetId.generate(), transport, MARCH, Money.ofCents(1), WHEN);

            BudgetProgress ruined =
                    tiny.progressWith(Money.ofCents(Entry.MAX_ABSOLUTE_CENTS));

            assertThat(ruined.percentageUsed())
                    .isEqualTo(BudgetProgress.HIGHEST_REPORTED_PERCENTAGE);
            assertThat(ruined.isExceeded()).isTrue();
        }

        /**
         * A refund is a negative expense, so spending can be below zero. The
         * honest answer is a negative percentage, not a crash and not a zero.
         */
        @Test
        @DisplayName("N17 · a refunded month reads as a negative percentage")
        void aRefundedMonthIsNegative() {
            BudgetProgress refunded = budget.progressWith(Money.ofUnits(-50_000));

            assertThat(refunded.percentageUsed()).isEqualTo(-10);
            assertThat(refunded.isExceeded()).isFalse();
            assertThat(refunded.isNearTheLimit()).isFalse();
        }
    }
}

package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block E: retiring accounts and categories (HU08, HU11).
 *
 * <p>The decisions under test are the ones taken for this project: a category
 * retires with its history, an account that still holds money does not retire at
 * all, and nothing is ever deleted.
 */
@DisplayName("Archiving")
class ArchivingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final LocalDate NEXT_MONTH = LocalDate.of(2026, 11, 6);
    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-06T21:00:00Z");

    private final Chart chart = new Chart();
    private final Ledger ledger = new Ledger();

    private final Account cash = chart.open(AccountType.ASSET, "Efectivo");
    private final Account nequi = chart.open(AccountType.ASSET, "Nequi");
    private final Account food = chart.open(AccountType.EXPENSE, "Alimentación");
    private final Account salary = chart.open(AccountType.INCOME, "Salario");
    private final Account momsFund = chart.open(AccountType.THIRD_PARTY_FUND, "Mamá");
    private final Account openingBalances = chart.open(AccountType.EQUITY, "Saldos iniciales");

    private void startWith(Account account, long amount) {
        ledger.record(Operations.openingBalance(
                TODAY, account, openingBalances, ofUnits(amount)));
    }

    @Nested
    @DisplayName("a category")
    class Categories {

        @Test
        @DisplayName("E1 · retires even with movements, and keeps its name in history")
        void archivesWithHistory() {
            startWith(cash, 100_000);
            ledger.record(Operations.expense(TODAY, "Almuerzo", cash, food, ofUnits(35_000)));

            chart.archive(food.id(), ledger, NOW);

            assertThat(food.isArchived()).isTrue();
            assertThat(ledger.displayedBalanceOf(food, TODAY)).isEqualTo(ofUnits(35_000));
            assertThat(ledger.movementsOf(food)).hasSize(1);
            assertThat(chart.all()).contains(food);
        }

        @Test
        @DisplayName("E1b · still prints on a report for a month it was active")
        void stillNamedInReports() {
            startWith(cash, 100_000);
            ledger.record(Operations.expense(TODAY, "Almuerzo", cash, food, ofUnits(35_000)));
            chart.archive(food.id(), ledger, NOW);

            assertThat(food.name()).isEqualTo("Alimentación");
            assertThat(ledger.totalFor(AccountType.EXPENSE, TODAY)).isEqualTo(ofUnits(35_000));
        }
    }

    @Nested
    @DisplayName("an account that holds money")
    class MoneyHoldingAccounts {

        @Test
        @DisplayName("E2 · refuses to retire while it still has a balance")
        void refusesWithABalance() {
            startWith(cash, 50_000);

            assertThatExceptionOfType(AccountStillHoldsMoneyException.class)
                    .isThrownBy(() -> chart.archive(cash.id(), ledger, NOW))
                    .satisfies(thrown -> assertThat(thrown.account()).isEqualTo(cash));

            assertThat(cash.isArchived()).isFalse();
        }

        @Test
        @DisplayName("E3 · retires once it is empty, history and all")
        void archivesWhenEmptied() {
            startWith(cash, 100_000);
            ledger.record(Operations.transfer(TODAY, "Vacío la caja", cash, nequi,
                    ofUnits(100_000)));

            assertThatNoException().isThrownBy(() -> chart.archive(cash.id(), ledger, NOW));

            assertThat(cash.isArchived()).isTrue();
            assertThat(ledger.movementsOf(cash)).hasSize(2);
        }

        @Test
        @DisplayName("E5 · refuses when a scheduled movement would leave money in it")
        void refusesWhenScheduledMovementsLeaveMoney() {
            // Empty today, but next month's pay lands here. Checking only today's
            // balance would let this account be retired with money on the way in.
            ledger.record(Operations.income(
                    NEXT_MONTH, "Quincena", cash, salary, ofUnits(50_000)));

            assertThat(ledger.displayedBalanceOf(cash, TODAY)).isEqualTo(Money.ZERO);
            assertThat(ledger.displayedBalanceOf(cash, NEXT_MONTH)).isEqualTo(ofUnits(50_000));

            assertThatExceptionOfType(AccountStillHoldsMoneyException.class)
                    .isThrownBy(() -> chart.archive(cash.id(), ledger, NOW));
        }

        @Test
        @DisplayName("E5b · retires when its own closing movement is already booked")
        void archivesWhenTheExitIsAlreadyBooked() {
            // 40,000 in it today, and the movement that empties it is already
            // recorded for next month. The account is on its way out, so it goes.
            startWith(cash, 40_000);
            ledger.record(Operations.expense(
                    NEXT_MONTH, "Último gasto", cash, food, ofUnits(40_000)));

            assertThat(ledger.displayedBalanceOf(cash, TODAY)).isEqualTo(ofUnits(40_000));

            assertThatNoException().isThrownBy(() -> chart.archive(cash.id(), ledger, NOW));
        }

        @Test
        @DisplayName("E5c · a third-party fund cannot be retired with money still in it")
        void refusesAFundWithMoney() {
            ledger.record(Operations.receiveThirdPartyMoney(
                    TODAY, "Mamá me deja plata", momsFund, cash, ofUnits(100_000)));

            assertThatExceptionOfType(AccountStillHoldsMoneyException.class)
                    .isThrownBy(() -> chart.archive(momsFund.id(), ledger, NOW));
        }

        @Test
        @DisplayName("E5d · a fund retires once it has been handed back in full")
        void archivesAFundOnceReturned() {
            ledger.record(Operations.receiveThirdPartyMoney(
                    TODAY, "Entrega", momsFund, cash, ofUnits(100_000)));
            ledger.record(Operations.returnThirdPartyMoney(
                    TODAY, "Le devuelvo", momsFund, cash, ofUnits(100_000)));

            assertThatNoException().isThrownBy(() -> chart.archive(momsFund.id(), ledger, NOW));
        }

        @Test
        @DisplayName("E5e · a cancelled movement does not keep an account alive")
        void cancelledMovementsDoNotCount() {
            startWith(cash, 50_000);
            Transaction mistake = Operations.expense(
                    NEXT_MONTH, "Error", cash, food, ofUnits(10_000));
            ledger.record(mistake);
            ledger.cancel(mistake.id(), NOW, "No era");

            ledger.record(Operations.transfer(TODAY, "Vacío", cash, nequi, ofUnits(50_000)));

            assertThatNoException().isThrownBy(() -> chart.archive(cash.id(), ledger, LATER));
        }
    }

    @Nested
    @DisplayName("an archived account")
    class ArchivedAccounts {

        @Test
        @DisplayName("E4 · drops out of the selector for new movements")
        void disappearsFromTheSelector() {
            assertThat(chart.selectableOf(AccountType.ASSET)).contains(cash, nequi);

            startWith(cash, 100_000);
            ledger.record(Operations.transfer(TODAY, "Vacío", cash, nequi, ofUnits(100_000)));
            chart.archive(cash.id(), ledger, NOW);

            assertThat(chart.selectableOf(AccountType.ASSET)).containsExactly(nequi);
            assertThat(chart.archived()).containsExactly(cash);
            assertThat(chart.allOf(AccountType.ASSET)).contains(cash, nequi);
        }

        @Test
        @DisplayName("E4b · takes no new movements")
        void takesNoNewEntries() {
            chart.archive(food.id(), ledger, NOW);

            assertThatExceptionOfType(ArchivedAccountException.class)
                    .isThrownBy(() -> Operations.expense(
                            TODAY, "Tarde", nequi, food, ofUnits(10_000)));
        }

        @Test
        @DisplayName("E6 · cannot be archived a second time")
        void archivesOnlyOnce() {
            chart.archive(food.id(), ledger, NOW);

            assertThatExceptionOfType(AccountAlreadyArchivedException.class)
                    .isThrownBy(() -> chart.archive(food.id(), ledger, LATER));
        }

        @Test
        @DisplayName("E6b · frees its name for a new account of the same kind")
        void freesItsName() {
            chart.archive(food.id(), ledger, NOW);

            Account reopened = chart.open(AccountType.EXPENSE, "Alimentación");

            assertThat(reopened.id()).isNotEqualTo(food.id());
            assertThat(chart.selectableOf(AccountType.EXPENSE)).containsExactly(reopened);
        }
    }

    @Test
    @DisplayName("E7 · archiving changes no balance anywhere")
    void archivingChangesNoBalance() {
        startWith(cash, 100_000);
        ledger.record(Operations.expense(TODAY, "Almuerzo", cash, food, ofUnits(35_000)));

        Money cashBefore = ledger.displayedBalanceOf(cash, TODAY);
        Money foodBefore = ledger.displayedBalanceOf(food, TODAY);
        Money mineBefore = ledger.moneyReallyMine(TODAY);

        chart.archive(food.id(), ledger, NOW);

        assertThat(ledger.displayedBalanceOf(cash, TODAY)).isEqualTo(cashBefore);
        assertThat(ledger.displayedBalanceOf(food, TODAY)).isEqualTo(foodBefore);
        assertThat(ledger.moneyReallyMine(TODAY)).isEqualTo(mineBefore);
        assertThat(ledger.sumOfEveryEntry()).isEqualTo(Money.ZERO);
    }
}

package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block B: the seven operations from the user stories.
 *
 * <p>Each test states the whole truth about one operation, including what it
 * must leave alone. "The loan did not change my expenses" is the assertion that
 * makes this app different from every other expense tracker.
 */
@DisplayName("The operations")
class OperationsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    private final Account bancolombia = TestAccounts.bancolombia();
    private final Account nequi = TestAccounts.nequi();
    private final Account cash = TestAccounts.cash();
    private final Account food = TestAccounts.food();
    private final Account salary = TestAccounts.salary();
    private final Account juan = TestAccounts.owedByJuan();
    private final Account brother = TestAccounts.owedToBrother();
    private final Account momsFund = TestAccounts.momsFund();
    private final Account openingBalances = TestAccounts.openingBalances();

    private final Ledger ledger = new Ledger();

    private Money shown(Account account) {
        return ledger.displayedBalanceOf(account, TODAY);
    }

    private Money totalOf(AccountType type) {
        return ledger.totalFor(type, TODAY);
    }

    @Test
    @DisplayName("B0 · an opening balance starts the book without inventing money")
    void openingBalance() {
        ledger.record(Operations.openingBalance(
                TODAY, bancolombia, openingBalances, ofUnits(500_000)));

        assertThat(shown(bancolombia)).isEqualTo(ofUnits(500_000));
        assertThat(ledger.sumOfEveryEntry()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("B0b · an opening balance works for a debt you already had")
    void openingDebt() {
        ledger.record(Operations.openingBalance(
                TODAY, brother, openingBalances, ofUnits(500_000)));

        assertThat(shown(brother)).isEqualTo(ofUnits(500_000));
    }

    @Test
    @DisplayName("B1 · income raises the account and the income total")
    void income() {
        ledger.record(Operations.income(
                TODAY, "Quincena", bancolombia, salary, ofUnits(2_000_000)));

        assertThat(shown(bancolombia)).isEqualTo(ofUnits(2_000_000));
        assertThat(totalOf(AccountType.INCOME)).isEqualTo(ofUnits(2_000_000));
        assertThat(totalOf(AccountType.EXPENSE)).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("B2 · an expense lowers the account and raises the category")
    void expense() {
        ledger.record(Operations.openingBalance(TODAY, cash, openingBalances, ofUnits(100_000)));
        ledger.record(Operations.expense(TODAY, "Almuerzo", cash, food, ofUnits(35_000)));

        assertThat(shown(cash)).isEqualTo(ofUnits(65_000));
        assertThat(shown(food)).isEqualTo(ofUnits(35_000));
        assertThat(totalOf(AccountType.EXPENSE)).isEqualTo(ofUnits(35_000));
    }

    @Nested
    @DisplayName("that make this app different")
    class TheOnesThatMatter {

        @Test
        @DisplayName("B5 · a transfer moves money without touching income or expenses")
        void transferIsNeitherIncomeNorExpense() {
            ledger.record(Operations.openingBalance(
                    TODAY, bancolombia, openingBalances, ofUnits(500_000)));

            ledger.record(Operations.transfer(
                    TODAY, "A Nequi", bancolombia, nequi, ofUnits(100_000)));

            assertThat(shown(bancolombia)).isEqualTo(ofUnits(400_000));
            assertThat(shown(nequi)).isEqualTo(ofUnits(100_000));

            assertThat(totalOf(AccountType.INCOME)).isEqualTo(Money.ZERO);
            assertThat(totalOf(AccountType.EXPENSE)).isEqualTo(Money.ZERO);
            assertThat(ledger.moneyReallyMine(TODAY)).isEqualTo(ofUnits(500_000));
        }

        @Test
        @DisplayName("B6 · lending money is not an expense, it is money you still own")
        void lendingIsNotAnExpense() {
            ledger.record(Operations.openingBalance(
                    TODAY, bancolombia, openingBalances, ofUnits(500_000)));

            ledger.record(Operations.lend(
                    TODAY, "Préstamo a Juan", bancolombia, juan, ofUnits(300_000)));

            assertThat(shown(bancolombia)).isEqualTo(ofUnits(200_000));
            assertThat(shown(juan)).isEqualTo(ofUnits(300_000));
            assertThat(totalOf(AccountType.EXPENSE)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("B7 · spending someone else's money is not your expense")
        void spendingThirdPartyMoneyIsNotYourExpense() {
            ledger.record(Operations.openingBalance(
                    TODAY, cash, openingBalances, ofUnits(50_000)));
            ledger.record(Operations.receiveThirdPartyMoney(
                    TODAY, "Mamá me deja plata", momsFund, cash, ofUnits(100_000)));

            ledger.record(Operations.spendThirdPartyMoney(
                    TODAY, "Factura de mamá", momsFund, cash, ofUnits(30_000)));

            assertThat(shown(cash)).isEqualTo(ofUnits(120_000));
            assertThat(shown(momsFund)).isEqualTo(ofUnits(70_000));

            assertThat(totalOf(AccountType.EXPENSE)).isEqualTo(Money.ZERO);
            assertThat(totalOf(AccountType.INCOME)).isEqualTo(Money.ZERO);
            assertThat(ledger.moneyReallyMine(TODAY)).isEqualTo(ofUnits(50_000));
        }
    }

    @Nested
    @DisplayName("that settle a balance")
    class Settling {

        @Test
        @DisplayName("B8 · a partial repayment leaves the rest pending (HU14)")
        void partialRepayment() {
            ledger.record(Operations.openingBalance(
                    TODAY, bancolombia, openingBalances, ofUnits(500_000)));
            ledger.record(Operations.lend(
                    TODAY, "Préstamo a Juan", bancolombia, juan, ofUnits(300_000)));

            ledger.record(Operations.collectLoan(
                    TODAY, "Juan abona", juan, bancolombia, ofUnits(100_000)));

            assertThat(shown(juan)).isEqualTo(ofUnits(200_000));
            assertThat(shown(bancolombia)).isEqualTo(ofUnits(300_000));
        }

        @Test
        @DisplayName("B9 · borrowing then paying part of it back (HU15, HU16)")
        void borrowAndRepay() {
            ledger.record(Operations.borrow(
                    TODAY, "Mi hermano me presta", brother, cash, ofUnits(500_000)));

            assertThat(shown(brother)).isEqualTo(ofUnits(500_000));
            assertThat(shown(cash)).isEqualTo(ofUnits(500_000));
            assertThat(ledger.moneyReallyMine(TODAY)).isEqualTo(Money.ZERO);

            ledger.record(Operations.repayDebt(
                    TODAY, "Abono al hermano", cash, brother, ofUnits(200_000)));

            assertThat(shown(brother)).isEqualTo(ofUnits(300_000));
            assertThat(shown(cash)).isEqualTo(ofUnits(300_000));
            assertThat(totalOf(AccountType.EXPENSE)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("B10 · giving a fund back empties it (HU20)")
        void returningThirdPartyMoney() {
            ledger.record(Operations.receiveThirdPartyMoney(
                    TODAY, "Mamá me deja plata", momsFund, cash, ofUnits(100_000)));
            ledger.record(Operations.spendThirdPartyMoney(
                    TODAY, "Factura", momsFund, cash, ofUnits(30_000)));

            ledger.record(Operations.returnThirdPartyMoney(
                    TODAY, "Le devuelvo a mamá", momsFund, cash, ofUnits(70_000)));

            assertThat(shown(momsFund)).isEqualTo(Money.ZERO);
            assertThat(shown(cash)).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("refuse")
    class Refuse {

        @Test
        @DisplayName("B11 · a negative or zero amount")
        void nonPositiveAmount() {
            assertThatExceptionOfType(NonPositiveAmountException.class)
                    .isThrownBy(() -> Operations.expense(
                            TODAY, "Raro", cash, food, ofUnits(-10_000)));

            assertThatExceptionOfType(NonPositiveAmountException.class)
                    .isThrownBy(() -> Operations.expense(TODAY, "Raro", cash, food, Money.ZERO));
        }

        @Test
        @DisplayName("B12 · an account of the wrong kind")
        void wrongAccountType() {
            assertThatExceptionOfType(UnexpectedAccountTypeException.class)
                    .isThrownBy(() -> Operations.expense(
                            TODAY, "Imposible", salary, food, ofUnits(10_000)))
                    .satisfies(thrown -> assertThat(thrown.account()).isEqualTo(salary));

            assertThatExceptionOfType(UnexpectedAccountTypeException.class)
                    .isThrownBy(() -> Operations.lend(
                            TODAY, "Imposible", bancolombia, momsFund, ofUnits(10_000)));
        }

        @Test
        @DisplayName("B13 · a transfer to the same account")
        void transferToItself() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Operations.transfer(
                            TODAY, "Circular", nequi, nequi, ofUnits(10_000)));
        }
    }
}

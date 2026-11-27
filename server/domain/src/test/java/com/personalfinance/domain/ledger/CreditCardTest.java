package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Block T: credit cards (8a-bis).
 *
 * <p>A card is a debt that belongs to nobody. Two rules carry the whole block:
 * the expense happens the day the card is swiped, and paying the card is not an
 * expense — otherwise the same lunch would count twice in the month and in the
 * budget.
 */
@DisplayName("Credit cards")
class CreditCardTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private final Account bancolombia = TestAccounts.bancolombia();
    private final Account card = TestAccounts.nuCard();
    private final Account food = TestAccounts.food();
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

    private void owedOnTheCard(long units) {
        ledger.record(Operations.openingBalance(TransactionId.generate(),
                TODAY, card, openingBalances, ofUnits(units)));
    }

    private void inTheBank(long units) {
        ledger.record(Operations.openingBalance(TransactionId.generate(),
                TODAY, bancolombia, openingBalances, ofUnits(units)));
    }

    @Test
    @DisplayName("T1 · a card is a debt: it reads like what you owe, and it is nobody's")
    void aCardIsADebtOfNobody() {
        assertThat(AccountType.CREDIT_CARD.nature()).isEqualTo(BalanceNature.CREDIT);
        assertThat(AccountType.CREDIT_CARD.requiresZeroBalanceToArchive()).isTrue();
        assertThat(AccountType.CREDIT_CARD.belongsToAPerson()).isFalse();
    }

    @Test
    @DisplayName("T2 · the opening balance of a card is what you already owe on it")
    void openingBalanceOfACard() {
        owedOnTheCard(800_000);

        assertThat(shown(card)).isEqualTo(ofUnits(800_000));
        assertThat(ledger.moneyReallyMine(TODAY)).isEqualTo(ofUnits(-800_000));
        assertThat(ledger.sumOfEveryEntry()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("T3 · swiping the card is the expense: the debt and the category go up, the bank is untouched")
    void anExpenseWithTheCard() {
        inTheBank(1_000_000);

        ledger.record(Operations.expense(TransactionId.generate(),
                TODAY, "Spotify", card, food, ofUnits(20_000)));

        assertThat(shown(card)).isEqualTo(ofUnits(20_000));
        assertThat(totalOf(AccountType.EXPENSE)).isEqualTo(ofUnits(20_000));
        assertThat(shown(bancolombia)).isEqualTo(ofUnits(1_000_000));
        assertThat(ledger.moneyReallyMine(TODAY)).isEqualTo(ofUnits(980_000));
    }

    @Test
    @DisplayName("T4 · paying the card lowers the debt and the bank, and it is not an expense")
    void payingTheCard() {
        inTheBank(1_000_000);
        owedOnTheCard(300_000);
        Money mineBefore = ledger.moneyReallyMine(TODAY);

        ledger.record(Operations.payCard(TransactionId.generate(),
                TODAY, "Pago NU", bancolombia, card, ofUnits(100_000)));

        assertThat(shown(card)).isEqualTo(ofUnits(200_000));
        assertThat(shown(bancolombia)).isEqualTo(ofUnits(900_000));
        assertThat(totalOf(AccountType.EXPENSE)).isEqualTo(Money.ZERO);
        assertThat(ledger.moneyReallyMine(TODAY)).isEqualTo(mineBefore);
    }

    @Test
    @DisplayName("T5 · what is really mine takes the card's debt out")
    void moneyReallyMineSubtractsTheCard() {
        inTheBank(1_000_000);
        owedOnTheCard(300_000);

        assertThat(ledger.moneyReallyMine(TODAY)).isEqualTo(ofUnits(700_000));
    }

    @Test
    @DisplayName("T6 · an expense is still only paid from money or a card, never from someone's account")
    void anExpenseIsNotPaidFromAPersonsAccount() {
        for (Account notMoney : new Account[] {juan, brother, momsFund, openingBalances, food}) {
            assertThatExceptionOfType(UnexpectedAccountTypeException.class)
                    .isThrownBy(() -> Operations.expense(TransactionId.generate(),
                            TODAY, "Almuerzo", notMoney, food, ofUnits(20_000)));
        }
    }

    @Test
    @DisplayName("T7 · a card is paid from money and only a card is paid")
    void payCardChecksBothSides() {
        Account nequi = TestAccounts.nequi();

        assertThatExceptionOfType(UnexpectedAccountTypeException.class)
                .as("from a card")
                .isThrownBy(() -> Operations.payCard(TransactionId.generate(),
                        TODAY, "Pago", card, TestAccounts.nuCard(), ofUnits(1_000)));
        assertThatExceptionOfType(UnexpectedAccountTypeException.class)
                .as("to a bank account")
                .isThrownBy(() -> Operations.payCard(TransactionId.generate(),
                        TODAY, "Pago", bancolombia, nequi, ofUnits(1_000)));
        assertThatExceptionOfType(UnexpectedAccountTypeException.class)
                .as("to what you owe a person")
                .isThrownBy(() -> Operations.payCard(TransactionId.generate(),
                        TODAY, "Pago", bancolombia, brother, ofUnits(1_000)));
        assertThatExceptionOfType(NonPositiveAmountException.class)
                .isThrownBy(() -> Operations.payCard(TransactionId.generate(),
                        TODAY, "Pago", bancolombia, card, Money.ZERO));
    }

    @Test
    @DisplayName("T8 · a card that still has debt cannot be archived; a paid-off one can")
    void archivingACard() {
        Chart chart = new Chart();
        Account inTheChart = chart.open(AccountType.CREDIT_CARD, "Tarjeta NU");
        Account bank = chart.open(AccountType.ASSET, "Bancolombia");
        Account equity = chart.open(AccountType.EQUITY, "Saldos iniciales");
        ledger.record(Operations.openingBalance(TransactionId.generate(),
                TODAY, inTheChart, equity, ofUnits(50_000)));

        assertThatExceptionOfType(AccountStillHoldsMoneyException.class)
                .isThrownBy(() -> chart.archive(inTheChart.id(), ledger, Instant.now()));

        ledger.record(Operations.payCard(TransactionId.generate(),
                TODAY, "Pago total", bank, inTheChart, ofUnits(50_000)));
        chart.archive(inTheChart.id(), ledger, Instant.now());

        assertThat(inTheChart.isArchived()).isTrue();
    }

    @Test
    @DisplayName("T9 · a card cannot belong to a person")
    void aCardIsNobodys() {
        Chart chart = new Chart();
        Account inTheChart = chart.open(AccountType.CREDIT_CARD, "Tarjeta NU");
        Person juanHimself = new Directory().open("Juan");

        assertThatExceptionOfType(AccountCannotBelongToAPersonException.class)
                .isThrownBy(() -> chart.linkToPerson(inTheChart.id(), juanHimself));
    }
}

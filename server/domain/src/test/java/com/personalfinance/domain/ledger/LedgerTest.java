package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Block B, second half: what the ledger answers once the operations are in it.
 */
@DisplayName("The ledger")
class LedgerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 6);
    private static final LocalDate NEXT_MONTH = LocalDate.of(2026, 11, 6);

    private final Account bancolombia = TestAccounts.bancolombia();
    private final Account cash = TestAccounts.cash();
    private final Account food = TestAccounts.food();
    private final Account brother = TestAccounts.owedToBrother();
    private final Account juan = TestAccounts.owedByJuan();
    private final Account momsFund = TestAccounts.momsFund();
    private final Account openingBalances = TestAccounts.openingBalances();

    private final Ledger ledger = new Ledger();

    @Test
    @DisplayName("B14 · answers as of a date, ignoring what has not happened yet")
    void futureTransactionsDoNotCount() {
        ledger.record(Operations.openingBalance(TransactionId.generate(),
                MONDAY, cash, openingBalances, ofUnits(100_000)));
        ledger.record(Operations.expense(TransactionId.generate(),
                NEXT_MONTH, "Arriendo programado", cash, food, ofUnits(40_000)));

        assertThat(ledger.displayedBalanceOf(cash, TUESDAY)).isEqualTo(ofUnits(100_000));
        assertThat(ledger.displayedBalanceOf(cash, NEXT_MONTH)).isEqualTo(ofUnits(60_000));
    }

    @Test
    @DisplayName("B15 · tells you what is really yours (HU22)")
    void moneyReallyMine() {
        ledger.record(Operations.openingBalance(TransactionId.generate(),
                MONDAY, bancolombia, openingBalances, ofUnits(500_000)));
        ledger.record(Operations.receiveThirdPartyMoney(TransactionId.generate(),
                MONDAY, "Mamá", momsFund, bancolombia, ofUnits(100_000)));
        ledger.record(Operations.borrow(TransactionId.generate(),
                MONDAY, "Mi hermano", brother, bancolombia, ofUnits(200_000)));

        // The bank says 800,000. Only 500,000 of it is actually yours.
        assertThat(ledger.displayedBalanceOf(bancolombia, TUESDAY)).isEqualTo(ofUnits(800_000));
        assertThat(ledger.moneyReallyMine(TUESDAY)).isEqualTo(ofUnits(500_000));
    }

    @Test
    @DisplayName("B16 · lists the movements of one fund, oldest first (HU21)")
    void movementsOfAFund() {
        ledger.record(Operations.receiveThirdPartyMoney(TransactionId.generate(),
                MONDAY, "Entrega", momsFund, cash, ofUnits(100_000)));
        ledger.record(Operations.expense(TransactionId.generate(),
                MONDAY, "Mi almuerzo", cash, food, ofUnits(20_000)));
        ledger.record(Operations.spendThirdPartyMoney(TransactionId.generate(),
                TUESDAY, "Pago factura", momsFund, cash, ofUnits(30_000)));

        assertThat(ledger.movementsOf(momsFund))
                .extracting(Transaction::description)
                .containsExactly("Entrega", "Pago factura");
    }

    @Test
    @DisplayName("B17 · every entry ever written still adds up to zero")
    void theBookAlwaysBalances() {
        ledger.record(Operations.openingBalance(TransactionId.generate(),
                MONDAY, bancolombia, openingBalances, ofUnits(500_000)));
        ledger.record(Operations.expense(TransactionId.generate(),
                MONDAY, "Almuerzo", bancolombia, food, ofUnits(35_000)));
        ledger.record(Operations.lend(TransactionId.generate(),
                MONDAY, "A Juan", bancolombia, juan, ofUnits(300_000)));
        ledger.record(Operations.collectLoan(TransactionId.generate(),
                TUESDAY, "Juan abona", juan, bancolombia, ofUnits(100_000)));
        ledger.record(Operations.receiveThirdPartyMoney(TransactionId.generate(),
                TUESDAY, "Mamá", momsFund, cash, ofUnits(100_000)));
        ledger.record(Operations.spendThirdPartyMoney(TransactionId.generate(),
                TUESDAY, "Factura", momsFund, cash, ofUnits(30_000)));

        assertThat(ledger.sumOfEveryEntry()).isEqualTo(Money.ZERO);
    }

    /**
     * What the use cases ask before writing: is this id already in the book?
     * Scanning {@link Ledger#history()} would answer the same thing and invite
     * every caller to write that loop again.
     */
    @Test
    @DisplayName("B19 · finds a movement by its id, and says nothing about one it never saw")
    void findsAMovementById() {
        Transaction lunch = Operations.expense(
                TransactionId.generate(), MONDAY, "Almuerzo", cash, food, ofUnits(35_000));
        ledger.record(lunch);

        assertThat(ledger.find(lunch.id())).contains(lunch);
        assertThat(ledger.find(TransactionId.generate())).isEmpty();
    }

    @Test
    @DisplayName("B18 · an empty ledger answers zero rather than failing")
    void emptyLedger() {
        assertThat(ledger.displayedBalanceOf(cash, TUESDAY)).isEqualTo(Money.ZERO);
        assertThat(ledger.totalFor(AccountType.EXPENSE, TUESDAY)).isEqualTo(Money.ZERO);
        assertThat(ledger.moneyReallyMine(TUESDAY)).isEqualTo(Money.ZERO);
    }
}

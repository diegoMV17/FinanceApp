package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Block J, third part: HU19 and HU21, the reason this application exists.
 *
 * <p>"How do Juan and I stand?" is three numbers read at once — what he owes me,
 * what I owe him and how much of his money I am holding — and until now nothing
 * knew those three accounts were the same person.
 *
 * <p>Every number comes out with the sign the person reads, the one
 * {@link BalanceNature} decides. A fund of 50,000 reads 50,000, not -50,000.
 */
@DisplayName("A position with one person")
class PersonPositionTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);
    private static final Instant NOW = Instant.parse("2026-10-22T20:00:00Z");

    private final Directory directory = new Directory();
    private final Chart chart = new Chart();
    private final Ledger ledger = new Ledger();

    private Person juan;
    private Account bank;
    private Account owedByJuan;
    private Account owedToJuan;
    private Account juansFund;

    @BeforeEach
    void aBookWithJuanInIt() {
        juan = directory.open("Juan");

        bank = chart.open(AccountType.ASSET, "Bancolombia");
        owedByJuan = chart.open(AccountType.RECEIVABLE, "Juan");
        owedToJuan = chart.open(AccountType.PAYABLE, "Juan");
        juansFund = chart.open(AccountType.THIRD_PARTY_FUND, "Juan");

        chart.linkToPerson(owedByJuan.id(), juan);
        chart.linkToPerson(owedToJuan.id(), juan);
        chart.linkToPerson(juansFund.id(), juan);
    }

    @Test
    @DisplayName("J19 · adds up the three sides, each with the sign the person reads")
    void theThreeSides() {
        lend(100_000);
        borrow(30_000);
        receiveFund(50_000);

        PersonPosition position = PersonPosition.of(juan, chart, ledger, DAY);

        assertThat(position.id()).isEqualTo(juan.id());
        assertThat(position.name()).isEqualTo("Juan");
        assertThat(position.archivedAt()).isNull();
        assertThat(position.owedToYou()).isEqualTo(ofUnits(100_000));
        assertThat(position.youOwe()).isEqualTo(ofUnits(30_000));
        assertThat(position.theirFund()).isEqualTo(ofUnits(50_000));
    }

    /**
     * What would be left if everything with Juan were settled today. The fund
     * is subtracted for the same reason {@code moneyReallyMine} subtracts it:
     * the money is in my pocket but it is not mine.
     */
    @Test
    @DisplayName("J20 · and the net is what would be left after settling")
    void theNet() {
        lend(100_000);
        borrow(30_000);
        receiveFund(50_000);

        assertThat(PersonPosition.of(juan, chart, ledger, DAY).netOwedToYou())
                .isEqualTo(ofUnits(20_000));
    }

    @Test
    @DisplayName("J21 · counts several accounts of the same kind")
    void severalAccountsOfOneKind() {
        Account alsoOwedByJuan = chart.open(AccountType.RECEIVABLE, "Juan, el arriendo");
        chart.linkToPerson(alsoOwedByJuan.id(), juan);

        lend(100_000);
        record(Operations.lend(TransactionId.generate(), DAY, "Arriendo",
                bank, alsoOwedByJuan, ofUnits(400_000)));

        assertThat(PersonPosition.of(juan, chart, ledger, DAY).owedToYou())
                .isEqualTo(ofUnits(500_000));
    }

    @Test
    @DisplayName("J22 · stops counting what was cancelled")
    void cancelledMovementsDoNotCount() {
        Transaction loan = record(Operations.lend(TransactionId.generate(), DAY, "Préstamo",
                bank, owedByJuan, ofUnits(100_000)));

        ledger.cancel(loan.id(), NOW, "Me equivoqué de persona");

        assertThat(PersonPosition.of(juan, chart, ledger, DAY).owedToYou()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("J23 · answers as of a date, so a movement from tomorrow waits its turn")
    void answersAsOfADate() {
        record(Operations.lend(TransactionId.generate(), DAY.plusDays(1), "Préstamo",
                bank, owedByJuan, ofUnits(100_000)));

        assertThat(PersonPosition.of(juan, chart, ledger, DAY).owedToYou()).isEqualTo(Money.ZERO);
        assertThat(PersonPosition.of(juan, chart, ledger, DAY.plusDays(1)).owedToYou())
                .isEqualTo(ofUnits(100_000));
    }

    /**
     * A report for March still has to print what Juan owed in March, so an
     * archived account of his keeps counting. Today it reads zero — an account
     * that holds money cannot be archived with a balance — but asked about the
     * day of the loan it still has to answer 100,000.
     */
    @Test
    @DisplayName("J24 · still counts an archived account of theirs, back when it had a balance")
    void archivedAccountsStillCount() {
        record(Operations.lend(TransactionId.generate(), DAY.minusDays(2), "Préstamo",
                bank, owedByJuan, ofUnits(100_000)));
        record(Operations.collectLoan(TransactionId.generate(), DAY.minusDays(1), "Me pagó",
                owedByJuan, bank, ofUnits(100_000)));
        chart.archive(owedByJuan.id(), ledger, NOW);

        assertThat(PersonPosition.of(juan, chart, ledger, DAY).owedToYou())
                .isEqualTo(Money.ZERO);
        assertThat(PersonPosition.of(juan, chart, ledger, DAY.minusDays(2)).owedToYou())
                .isEqualTo(ofUnits(100_000));
    }

    @Test
    @DisplayName("J25 · leaves out the accounts of other people, and those of nobody")
    void onlyTheirOwnAccounts() {
        Person pedro = directory.open("Pedro");
        Account owedByPedro = chart.open(AccountType.RECEIVABLE, "Pedro");
        chart.linkToPerson(owedByPedro.id(), pedro);
        Account owedBySomebody = chart.open(AccountType.RECEIVABLE, "El del taller");

        lend(100_000);
        record(Operations.lend(TransactionId.generate(), DAY, "A Pedro",
                bank, owedByPedro, ofUnits(7_000)));
        record(Operations.lend(TransactionId.generate(), DAY, "Al del taller",
                bank, owedBySomebody, ofUnits(9_000)));

        assertThat(PersonPosition.of(juan, chart, ledger, DAY).owedToYou())
                .isEqualTo(ofUnits(100_000));
        assertThat(PersonPosition.of(pedro, chart, ledger, DAY).owedToYou())
                .isEqualTo(ofUnits(7_000));
    }

    @Test
    @DisplayName("J26 · reads zero for a person with no accounts, not nothing")
    void aPersonWithoutAccounts() {
        Person pedro = directory.open("Pedro");

        PersonPosition position = PersonPosition.of(pedro, chart, ledger, DAY);

        assertThat(position.owedToYou()).isEqualTo(Money.ZERO);
        assertThat(position.youOwe()).isEqualTo(Money.ZERO);
        assertThat(position.theirFund()).isEqualTo(Money.ZERO);
        assertThat(position.netOwedToYou()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("J27 · carries the archived date of a person who was archived")
    void carriesTheArchivedDate() {
        juan.archive(NOW);

        assertThat(PersonPosition.of(juan, chart, ledger, DAY).archivedAt()).isEqualTo(NOW);
    }

    private void lend(long units) {
        record(Operations.lend(TransactionId.generate(), DAY, "Préstamo",
                bank, owedByJuan, ofUnits(units)));
    }

    private void borrow(long units) {
        record(Operations.borrow(TransactionId.generate(), DAY, "Me prestó",
                owedToJuan, bank, ofUnits(units)));
    }

    private void receiveFund(long units) {
        record(Operations.receiveThirdPartyMoney(TransactionId.generate(), DAY, "Me dejó plata",
                juansFund, bank, ofUnits(units)));
    }

    private Transaction record(Transaction transaction) {
        ledger.record(transaction);
        return transaction;
    }
}

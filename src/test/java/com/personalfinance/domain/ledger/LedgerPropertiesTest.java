package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.domain.ledger.LedgerScenario.OperationKind;
import com.personalfinance.domain.ledger.LedgerScenario.Step;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Block D: the properties that must hold for every history, not just the ones
 * we thought to write down.
 *
 * <p>An example test proves the cases you imagined. These generate hundreds of
 * random sequences of operations and check that the book still makes sense
 * afterwards. This is where the bug you did not predict shows up.
 */
class LedgerPropertiesTest {

    private static final int LONGEST_HISTORY = 40;
    private static final int DAYS_COVERED = 60;

    @Property
    void everyOperationProducesABalancedTransaction(@ForAll("anyStep") Step step) {
        Transaction transaction = new LedgerScenario().transactionFor(step);

        Money total = Money.sum(transaction.entries().stream().map(Entry::amount).toList());

        assertThat(total).isEqualTo(Money.ZERO);
        assertThat(transaction.entries()).hasSize(2);
    }

    @Property
    void theBookAlwaysBalances(@ForAll("anyHistory") List<Step> history) {
        Ledger ledger = new LedgerScenario().ledgerFrom(history);

        assertThat(ledger.sumOfEveryEntry()).isEqualTo(Money.ZERO);
    }

    /**
     * Asking the full ledger "as of Tuesday" must give the same answer as
     * building a ledger that only ever saw what happened up to Tuesday.
     * If these ever disagree, the date filter is lying.
     */
    @Property
    void askingAsOfADateMatchesALedgerTruncatedThere(
            @ForAll("anyHistory") List<Step> history,
            @ForAll("anyDayOffset") int dayOffset) {

        LedgerScenario scenario = new LedgerScenario();
        LocalDate asOf = scenario.dayAfterStart(dayOffset);

        Ledger full = scenario.ledgerFrom(history);
        Ledger truncated = scenario.ledgerTruncatedAt(history, asOf);

        for (Account account : scenario.allAccounts) {
            assertThat(full.balanceOf(account, asOf))
                    .isEqualTo(truncated.balanceOf(account, asOf));
        }
    }

    /**
     * Whatever you do with other people's money, none of it ever becomes yours.
     * This is the invariant the whole app exists for.
     */
    @Property
    void thirdPartyMoneyIsNeverYours(@ForAll("thirdPartyHistory") List<Step> history) {
        LedgerScenario scenario = new LedgerScenario();
        Ledger ledger = scenario.ledgerFrom(history);
        LocalDate theEnd = scenario.dayAfterStart(DAYS_COVERED);

        assertThat(ledger.moneyReallyMine(theEnd)).isEqualTo(Money.ZERO);
        assertThat(ledger.totalFor(AccountType.EXPENSE, theEnd)).isEqualTo(Money.ZERO);
        assertThat(ledger.totalFor(AccountType.INCOME, theEnd)).isEqualTo(Money.ZERO);
    }

    /** Moving your own money around never creates or destroys any of it. */
    @Property
    void transfersNeverChangeWhatYouHave(@ForAll("transferHistory") List<Step> history) {
        LedgerScenario scenario = new LedgerScenario();
        LocalDate theEnd = scenario.dayAfterStart(DAYS_COVERED);

        Ledger ledger = scenario.ledgerFrom(history);

        assertThat(ledger.totalFor(AccountType.ASSET, theEnd)).isEqualTo(Money.ZERO);
        assertThat(ledger.moneyReallyMine(theEnd)).isEqualTo(Money.ZERO);
    }

    @Provide
    Arbitrary<Step> anyStep() {
        return stepsDrawnFrom(List.of(OperationKind.values()));
    }

    @Provide
    Arbitrary<List<Step>> anyHistory() {
        return anyStep().list().ofMaxSize(LONGEST_HISTORY);
    }

    @Provide
    Arbitrary<List<Step>> thirdPartyHistory() {
        return stepsDrawnFrom(OperationKind.THIRD_PARTY_ONLY).list().ofMaxSize(LONGEST_HISTORY);
    }

    @Provide
    Arbitrary<List<Step>> transferHistory() {
        return stepsDrawnFrom(List.of(OperationKind.TRANSFER)).list().ofMaxSize(LONGEST_HISTORY);
    }

    @Provide
    Arbitrary<Integer> anyDayOffset() {
        return Arbitraries.integers().between(0, DAYS_COVERED);
    }

    private Arbitrary<Step> stepsDrawnFrom(List<OperationKind> kinds) {
        return Combinators.combine(
                        Arbitraries.of(kinds),
                        Arbitraries.longs().between(1, 5_000_000),
                        Arbitraries.integers().between(0, DAYS_COVERED))
                .as(Step::new);
    }
}

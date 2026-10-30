package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.domain.ledger.LedgerScenario.OperationKind;
import com.personalfinance.domain.ledger.LedgerScenario.Step;
import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;
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
    private static final Instant CANCELLED_AT = Instant.parse("2026-06-01T12:00:00Z");
    private static final Instant ARCHIVED_AT = Instant.parse("2026-12-31T12:00:00Z");

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

    /**
     * The real meaning of cancelling: a ledger where you recorded A, B and C
     * and then cancelled B must read exactly like a ledger that only ever saw
     * A and C. Every account, every date. If these two ever disagree,
     * cancelling is leaking.
     */
    @Property
    void cancellingIsTheSameAsNeverHavingRecordedIt(
            @ForAll("anyHistory") List<Step> history,
            @ForAll("anyStride") int stride,
            @ForAll("anyDayOffset") int dayOffset) {

        IntPredicate cancelled = position -> position % stride == 0;

        LedgerScenario withCancellations = new LedgerScenario();
        Ledger amended = withCancellations.ledgerFromWithCancellations(
                history, cancelled, CANCELLED_AT);

        LedgerScenario asIfNeverHappened = new LedgerScenario();
        Ledger survivorsOnly = asIfNeverHappened.ledgerFrom(
                asIfNeverHappened.stepsSurviving(history, cancelled));

        LocalDate asOf = withCancellations.dayAfterStart(dayOffset);

        for (int index = 0; index < withCancellations.allAccounts.size(); index++) {
            Account inOne = withCancellations.allAccounts.get(index);
            Account inTheOther = asIfNeverHappened.allAccounts.get(index);

            assertThat(amended.balanceOf(inOne, asOf))
                    .isEqualTo(survivorsOnly.balanceOf(inTheOther, asOf));
        }
        assertThat(amended.sumOfEveryEntry()).isEqualTo(Money.ZERO);
    }

    /**
     * Archiving is bookkeeping, not an accounting entry. Retiring everything the
     * rules allow, at any point in any history, must leave every balance exactly
     * where it was.
     */
    @Property
    void archivingNeverMovesMoney(
            @ForAll("anyHistory") List<Step> history,
            @ForAll("anyDayOffset") int dayOffset) {

        LedgerScenario scenario = new LedgerScenario();
        Ledger ledger = scenario.ledgerFrom(history);
        LocalDate asOf = scenario.dayAfterStart(dayOffset);

        List<Money> before = scenario.allAccounts.stream()
                .map(account -> ledger.balanceOf(account, asOf))
                .toList();

        scenario.allAccounts.stream()
                .filter(account -> mayBeArchived(account, ledger))
                .forEach(account -> scenario.chart.archive(account.id(), ledger, ARCHIVED_AT));

        List<Money> after = scenario.allAccounts.stream()
                .map(account -> ledger.balanceOf(account, asOf))
                .toList();

        assertThat(after).isEqualTo(before);
        assertThat(ledger.sumOfEveryEntry()).isEqualTo(Money.ZERO);
    }

    /**
     * HU19. Nobody's money falls between the people.
     *
     * <p>Once every account that is somebody has a person behind it, adding one
     * side up across all of them has to give exactly what the ledger says for
     * that kind of account. If it ever does not, some of it is sitting in a
     * position nobody reads — which is the one way this view could lie while
     * every single balance stayed right.
     */
    @Property
    void nothingOfTheirsFallsOutsideTheirPosition(
            @ForAll("anyHistory") List<Step> history,
            @ForAll("anyDayOffset") int dayOffset) {

        LedgerScenario scenario = new LedgerScenario();
        List<Person> everybody = scenario.everybodyLinked();
        Ledger ledger = scenario.ledgerFrom(history);
        LocalDate asOf = scenario.dayAfterStart(dayOffset);

        List<PersonPosition> positions = everybody.stream()
                .map(person -> PersonPosition.of(person, scenario.chart, ledger, asOf))
                .toList();

        assertThat(sumOf(positions, PersonPosition::owedToYou))
                .isEqualTo(ledger.totalFor(AccountType.RECEIVABLE, asOf));
        assertThat(sumOf(positions, PersonPosition::youOwe))
                .isEqualTo(ledger.totalFor(AccountType.PAYABLE, asOf));
        assertThat(sumOf(positions, PersonPosition::theirFund))
                .isEqualTo(ledger.totalFor(AccountType.THIRD_PARTY_FUND, asOf));
    }

    /**
     * HU23. The one invariant an aggregation has: breaking a total apart and
     * adding the pieces back has to give the total again.
     *
     * <p>Every type, over any period of any random history. If this ever fails,
     * a pie chart and the number printed beside it disagree — and both of them
     * look perfectly reasonable on their own, which is why nobody would notice.
     */
    @Property
    void theBreakdownAlwaysAddsUpToItsTotal(
            @ForAll("anyHistory") List<Step> history,
            @ForAll("anyDayOffset") int from,
            @ForAll("anyDayOffset") int to) {

        LedgerScenario scenario = new LedgerScenario();
        Ledger ledger = scenario.ledgerFrom(history);

        MovementQuery period = MovementQuery.everything().between(
                scenario.dayAfterStart(Math.min(from, to)),
                scenario.dayAfterStart(Math.max(from, to)));

        for (AccountType type : AccountType.values()) {
            assertThat(Money.sum(ledger.totalsByAccount(type, period).values()))
                    .as("the breakdown of %s", type)
                    .isEqualTo(ledger.totalFor(type, period));
        }
    }

    /**
     * And the same total asked the two ways the book offers must agree: up to a
     * date is nothing but a period that starts at the beginning of the book.
     */
    @Property
    void aPeriodOpenAtTheStartIsTheSameAsUpToADate(
            @ForAll("anyHistory") List<Step> history,
            @ForAll("anyDayOffset") int dayOffset) {

        LedgerScenario scenario = new LedgerScenario();
        Ledger ledger = scenario.ledgerFrom(history);
        LocalDate asOf = scenario.dayAfterStart(dayOffset);

        MovementQuery openAtTheStart = MovementQuery.everything().between(null, asOf);

        for (AccountType type : AccountType.values()) {
            assertThat(ledger.totalFor(type, openAtTheStart))
                    .as("the total of %s", type)
                    .isEqualTo(ledger.totalFor(type, asOf));
        }
    }

    private static Money sumOf(
            List<PersonPosition> positions, Function<PersonPosition, Money> side) {

        return Money.sum(positions.stream().map(side).toList());
    }

    private static boolean mayBeArchived(Account account, Ledger ledger) {
        return !account.type().requiresZeroBalanceToArchive()
                || ledger.eventualBalanceOf(account).isZero();
    }

    @Provide
    Arbitrary<Integer> anyStride() {
        return Arbitraries.integers().between(1, 5);
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

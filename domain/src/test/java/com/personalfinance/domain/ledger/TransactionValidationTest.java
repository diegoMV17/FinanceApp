package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block A: a transaction cannot exist in an invalid state.
 *
 * <p>No Spring, no database, no Testcontainers. These run in milliseconds,
 * which is the whole point of keeping the rules in the domain.
 */
@DisplayName("A transaction")
class TransactionValidationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    private final Account bancolombia = TestAccounts.bancolombia();
    private final Account cash = TestAccounts.cash();
    private final Account food = TestAccounts.food();

    @Nested
    @DisplayName("refuses to be created when")
    class RefusesCreation {

        @Test
        @DisplayName("A1 · its entries do not sum to zero")
        void entriesDoNotBalance() {
            List<Entry> unbalanced = List.of(
                    Entry.of(cash, ofUnits(-35_000)),
                    Entry.of(food, ofUnits(35_050)));

            assertThatExceptionOfType(UnbalancedTransactionException.class)
                    .isThrownBy(() -> Transaction.record(TODAY, "Almuerzo", unbalanced))
                    .satisfies(thrown ->
                            assertThat(thrown.difference()).isEqualTo(ofUnits(50)));
        }

        @Test
        @DisplayName("A2 · it has a single entry")
        void onlyOneEntry() {
            List<Entry> lonely = List.of(Entry.of(cash, ofUnits(-35_000)));

            assertThatExceptionOfType(NotEnoughEntriesException.class)
                    .isThrownBy(() -> Transaction.record(TODAY, "Almuerzo", lonely))
                    .satisfies(thrown -> assertThat(thrown.actual()).isEqualTo(1));
        }

        @Test
        @DisplayName("A2b · it has no entries at all")
        void noEntries() {
            assertThatExceptionOfType(NotEnoughEntriesException.class)
                    .isThrownBy(() -> Transaction.record(TODAY, "Nada", List.of()));
        }
    }

    @Nested
    @DisplayName("rejects an entry that")
    class RejectsEntry {

        @Test
        @DisplayName("A3 · moves zero")
        void zeroAmount() {
            assertThatExceptionOfType(ZeroAmountEntryException.class)
                    .isThrownBy(() -> Entry.of(cash, Money.ZERO))
                    .satisfies(thrown -> assertThat(thrown.account()).isEqualTo(cash));
        }

        @Test
        @DisplayName("A5 · points at an archived account")
        void archivedAccount() {
            Account oldWallet = TestAccounts.archived(TestAccounts.nequi());

            assertThatExceptionOfType(ArchivedAccountException.class)
                    .isThrownBy(() -> Entry.of(oldWallet, ofUnits(10_000)));
        }
    }

    @Nested
    @DisplayName("is created")
    class IsCreated {

        @Test
        @DisplayName("A4 · when three entries sum to zero")
        void threeBalancedEntries() {
            List<Entry> split = List.of(
                    Entry.of(cash, ofUnits(-20_000)),
                    Entry.of(bancolombia, ofUnits(-15_000)),
                    Entry.of(food, ofUnits(35_000)));

            assertThatNoException()
                    .isThrownBy(() -> Transaction.record(TODAY, "Almuerzo a medias", split));
        }

        @Test
        @DisplayName("A4b · and reports its effect on each account it touches")
        void reportsItsEffect() {
            Transaction lunch = Transaction.record(TODAY, "Almuerzo", List.of(
                    Entry.of(cash, ofUnits(-35_000)),
                    Entry.of(food, ofUnits(35_000))));

            assertThat(lunch.effectOn(cash)).isEqualTo(ofUnits(-35_000));
            assertThat(lunch.effectOn(food)).isEqualTo(ofUnits(35_000));
            assertThat(lunch.effectOn(bancolombia)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("A4c · and cannot be altered through the list it was given")
        void entriesAreCopied() {
            Transaction lunch = Transaction.record(TODAY, "Almuerzo", List.of(
                    Entry.of(cash, ofUnits(-35_000)),
                    Entry.of(food, ofUnits(35_000))));

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> lunch.entries().clear());
        }
    }
}

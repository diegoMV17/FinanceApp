package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block C: correcting and removing movements (HU06, HU07).
 *
 * <p>The rule under test throughout: nothing is edited and nothing is deleted.
 * A balance always has a history that explains it.
 */
@DisplayName("Cancelling and amending")
class CancellationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-06T21:00:00Z");

    private final Account cash = TestAccounts.cash();
    private final Account food = TestAccounts.food();
    private final Account openingBalances = TestAccounts.openingBalances();

    private final Ledger ledger = new Ledger();

    private Money cashBalance() {
        return ledger.displayedBalanceOf(cash, TODAY);
    }

    private void startWith(long amount) {
        ledger.record(Operations.openingBalance(
                TODAY, cash, openingBalances, ofUnits(amount)));
    }

    private Transaction lunchOf(long amount) {
        return Operations.expense(TODAY, "Almuerzo", cash, food, ofUnits(amount));
    }

    @Nested
    @DisplayName("a cancellation")
    class Cancelling {

        @Test
        @DisplayName("C1 · puts the balance back where it was")
        void restoresTheBalance() {
            startWith(100_000);
            Transaction lunch = lunchOf(35_000);
            ledger.record(lunch);
            assertThat(cashBalance()).isEqualTo(ofUnits(65_000));

            ledger.cancel(lunch.id(), NOW, "Lo registré dos veces");

            assertThat(cashBalance()).isEqualTo(ofUnits(100_000));
            assertThat(ledger.totalFor(AccountType.EXPENSE, TODAY)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("C2 · leaves the row in the book with its reason")
        void keepsTheRow() {
            startWith(100_000);
            Transaction lunch = lunchOf(35_000);
            ledger.record(lunch);

            ledger.cancel(lunch.id(), NOW, "Lo registré dos veces");

            assertThat(ledger.history()).contains(lunch);
            assertThat(ledger.activeTransactions()).doesNotContain(lunch);
            assertThat(lunch.isCancelled()).isTrue();
            assertThat(lunch.cancellation().orElseThrow().reason())
                    .isEqualTo("Lo registré dos veces");
            assertThat(lunch.cancellation().orElseThrow().at()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("C2b · hides it from the movements of an account")
        void disappearsFromMovements() {
            startWith(100_000);
            Transaction lunch = lunchOf(35_000);
            ledger.record(lunch);

            ledger.cancel(lunch.id(), NOW, "Error");

            assertThat(ledger.movementsOf(food)).isEmpty();
        }

        @Test
        @DisplayName("C5 · happens once, and a second attempt is an error")
        void cannotBeCancelledTwice() {
            startWith(100_000);
            Transaction lunch = lunchOf(35_000);
            ledger.record(lunch);
            ledger.cancel(lunch.id(), NOW, "Error");

            assertThatExceptionOfType(TransactionAlreadyCancelledException.class)
                    .isThrownBy(() -> ledger.cancel(lunch.id(), LATER, "Otra vez"));
        }

        @Test
        @DisplayName("C5b · refuses a transaction the book never saw")
        void unknownTransaction() {
            assertThatExceptionOfType(UnknownTransactionException.class)
                    .isThrownBy(() -> ledger.cancel(TransactionId.generate(), NOW, "Fantasma"));
        }
    }

    @Nested
    @DisplayName("an amendment")
    class Amending {

        @Test
        @DisplayName("C3 · replaces the amount instead of adding to it")
        void replacesRatherThanAdds() {
            startWith(100_000);
            Transaction wrong = lunchOf(35_000);
            ledger.record(wrong);

            ledger.amend(wrong.id(), lunchOf(40_000), NOW);

            assertThat(cashBalance()).isEqualTo(ofUnits(60_000));
            assertThat(ledger.totalFor(AccountType.EXPENSE, TODAY)).isEqualTo(ofUnits(40_000));
        }

        @Test
        @DisplayName("C4 · cancels the old version and links the new one to it")
        void leavesANavigableChain() {
            startWith(100_000);
            Transaction wrong = lunchOf(35_000);
            ledger.record(wrong);

            Transaction corrected = ledger.amend(wrong.id(), lunchOf(40_000), NOW);

            assertThat(wrong.isCancelled()).isTrue();
            assertThat(corrected.isCancelled()).isFalse();
            assertThat(corrected.replaces()).contains(wrong.id());
            assertThat(corrected.id()).isNotEqualTo(wrong.id());
        }

        @Test
        @DisplayName("C4b · the chain survives several corrections in a row")
        void chainsAcrossSeveralCorrections() {
            startWith(100_000);
            Transaction first = lunchOf(35_000);
            ledger.record(first);

            Transaction second = ledger.amend(first.id(), lunchOf(40_000), NOW);
            Transaction third = ledger.amend(second.id(), lunchOf(42_000), LATER);

            assertThat(ledger.revisionsOf(first.id()))
                    .containsExactly(first, second, third);
            assertThat(cashBalance()).isEqualTo(ofUnits(58_000));
        }

        @Test
        @DisplayName("C4c · an amended movement cannot be amended again")
        void cannotAmendASupersededVersion() {
            startWith(100_000);
            Transaction first = lunchOf(35_000);
            ledger.record(first);
            ledger.amend(first.id(), lunchOf(40_000), NOW);

            assertThatExceptionOfType(TransactionAlreadyCancelledException.class)
                    .isThrownBy(() -> ledger.amend(first.id(), lunchOf(50_000), LATER));
        }
    }

    @Test
    @DisplayName("C6 · the same transaction cannot be recorded twice")
    void noDuplicates() {
        Transaction lunch = lunchOf(35_000);
        ledger.record(lunch);

        assertThatExceptionOfType(DuplicateTransactionException.class)
                .isThrownBy(() -> ledger.record(lunch));
    }

    @Test
    @DisplayName("C7 · the book still balances after cancellations and amendments")
    void theBookStillBalances() {
        startWith(100_000);
        Transaction lunch = lunchOf(35_000);
        ledger.record(lunch);
        Transaction corrected = ledger.amend(lunch.id(), lunchOf(40_000), NOW);
        ledger.cancel(corrected.id(), LATER, "Mejor no");

        assertThat(ledger.sumOfEveryEntry()).isEqualTo(Money.ZERO);
        assertThat(cashBalance()).isEqualTo(ofUnits(100_000));
    }
}

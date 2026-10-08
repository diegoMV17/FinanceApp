package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block F: loading history is not the same as creating it.
 *
 * <p>A stored account may be archived and a stored entry may point at it, which
 * a new entry never may. What loading must NOT relax is anything about the
 * numbers: a stored transaction that does not balance is a corrupt book, and
 * the right response is to find out loudly, at load time.
 */
@DisplayName("Rehydrating from storage")
class RehydrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);
    private static final Instant WHEN = Instant.parse("2026-10-07T12:00:00Z");

    private final Account cash = TestAccounts.cash();
    private final Account food = TestAccounts.food();

    @Nested
    @DisplayName("an account")
    class RehydratedAccount {

        @Test
        @DisplayName("F1 · keeps its id, type, name and archived state")
        void keepsEverything() {
            AccountId id = AccountId.generate();

            Account loaded = Account.rehydrated(id, AccountType.ASSET, "Nequi", WHEN);

            assertThat(loaded.id()).isEqualTo(id);
            assertThat(loaded.type()).isEqualTo(AccountType.ASSET);
            assertThat(loaded.name()).isEqualTo("Nequi");
            assertThat(loaded.archivedAt()).contains(WHEN);
        }

        @Test
        @DisplayName("F2 · is open when no archive date is stored")
        void openWhenNoDate() {
            Account loaded =
                    Account.rehydrated(AccountId.generate(), AccountType.EXPENSE, "Ocio", null);

            assertThat(loaded.isArchived()).isFalse();
        }

        @Test
        @DisplayName("F3 · still refuses a blank name")
        void blankNameStillRefused() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() ->
                            Account.rehydrated(AccountId.generate(), AccountType.ASSET, " ", null));
        }
    }

    @Nested
    @DisplayName("an entry")
    class RehydratedEntry {

        @Test
        @DisplayName("F4 · may point at an archived account, because history does")
        void archivedAccountAllowed() {
            Account archived = TestAccounts.archived(TestAccounts.nequi());

            Entry loaded = Entry.rehydrated(archived, ofUnits(10_000));

            assertThat(loaded.account()).isEqualTo(archived);
            assertThat(loaded.amount()).isEqualTo(ofUnits(10_000));
        }

        @Test
        @DisplayName("F5 · still refuses to move zero")
        void zeroStillRefused() {
            assertThatExceptionOfType(ZeroAmountEntryException.class)
                    .isThrownBy(() -> Entry.rehydrated(cash, Money.ZERO));
        }

        @Test
        @DisplayName("F5b · a new entry still refuses an archived account")
        void newEntryStillRefusesArchived() {
            Account archived = TestAccounts.archived(TestAccounts.nequi());

            assertThatExceptionOfType(ArchivedAccountException.class)
                    .isThrownBy(() -> Entry.of(archived, ofUnits(10_000)));
        }
    }

    @Nested
    @DisplayName("a transaction")
    class RehydratedTransaction {

        private final List<Entry> lunch = List.of(
                Entry.of(cash, ofUnits(-35_000)),
                Entry.of(food, ofUnits(35_000)));

        @Test
        @DisplayName("F6 · keeps its id, date, source and entries in order")
        void keepsEverything() {
            TransactionId id = TransactionId.generate();

            Transaction loaded = Transaction.rehydrated(
                    id, DAY, "Almuerzo", TransactionSource.BOT, lunch, null, null);

            assertThat(loaded.id()).isEqualTo(id);
            assertThat(loaded.date()).isEqualTo(DAY);
            assertThat(loaded.description()).isEqualTo("Almuerzo");
            assertThat(loaded.source()).isEqualTo(TransactionSource.BOT);
            assertThat(loaded.entries()).containsExactlyElementsOf(lunch);
            assertThat(loaded.isCancelled()).isFalse();
            assertThat(loaded.replaces()).isEmpty();
        }

        @Test
        @DisplayName("F7 · keeps the cancellation it was stored with")
        void keepsCancellation() {
            Cancellation stored = new Cancellation(WHEN, "Duplicada", TransactionSource.BOT);

            Transaction loaded = Transaction.rehydrated(
                    TransactionId.generate(), DAY, "Almuerzo", TransactionSource.APP,
                    lunch, null, stored);

            assertThat(loaded.isCancelled()).isTrue();
            assertThat(loaded.cancellation()).contains(stored);
        }

        @Test
        @DisplayName("F8 · keeps the transaction it replaces")
        void keepsReplaces() {
            TransactionId original = TransactionId.generate();

            Transaction loaded = Transaction.rehydrated(
                    TransactionId.generate(), DAY, "Almuerzo", TransactionSource.APP,
                    lunch, original, null);

            assertThat(loaded.replaces()).contains(original);
        }

        @Test
        @DisplayName("F9 · a stored cancellation can still not be cancelled again")
        void cannotBeCancelledTwice() {
            Ledger ledger = new Ledger();
            Transaction loaded = Transaction.rehydrated(
                    TransactionId.generate(), DAY, "Almuerzo", TransactionSource.APP, lunch, null,
                    new Cancellation(WHEN, "Duplicada", TransactionSource.APP));
            ledger.record(loaded);

            assertThatExceptionOfType(TransactionAlreadyCancelledException.class)
                    .isThrownBy(() -> ledger.cancel(loaded.id(), WHEN, "otra vez"));
        }

        @Test
        @DisplayName("F10 · is refused when the stored entries do not balance")
        void unbalancedIsRefused() {
            List<Entry> corrupt = List.of(
                    Entry.rehydrated(cash, ofUnits(-35_000)),
                    Entry.rehydrated(food, ofUnits(35_050)));

            assertThatExceptionOfType(UnbalancedTransactionException.class)
                    .isThrownBy(() -> Transaction.rehydrated(
                            TransactionId.generate(), DAY, "Almuerzo", TransactionSource.APP,
                            corrupt, null, null));
        }

        @Test
        @DisplayName("F11 · is refused when it has fewer than two entries")
        void tooFewEntriesIsRefused() {
            List<Entry> lonely = List.of(Entry.rehydrated(cash, ofUnits(-35_000)));

            assertThatExceptionOfType(NotEnoughEntriesException.class)
                    .isThrownBy(() -> Transaction.rehydrated(
                            TransactionId.generate(), DAY, "Almuerzo", TransactionSource.APP,
                            lonely, null, null));
        }
    }
}

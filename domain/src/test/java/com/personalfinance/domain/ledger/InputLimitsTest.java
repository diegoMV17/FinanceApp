package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofCents;
import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block G: the size of what comes in from outside.
 *
 * <p>These are not cosmetic limits. {@link Money} adds with
 * {@link Math#addExact}, so a single entry parked near {@code Long.MAX_VALUE}
 * would make every later sum throw, and since nothing is ever deleted the book
 * would stay broken forever. The text limits are the same idea one level up: a
 * newline inside a description is a forged line in a log or in a message.
 *
 * <p>Every limit is checked in the constructor, which means {@code rehydrated}
 * checks it too: a row somebody edited in the database has to fail on load, the
 * way an unbalanced one already does.
 */
@DisplayName("Input limits")
class InputLimitsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");

    private static final long MAX_CENTS = Entry.MAX_ABSOLUTE_CENTS;

    private final Account cash = TestAccounts.cash();
    private final Account food = TestAccounts.food();

    @Nested
    @DisplayName("on an amount")
    class Amounts {

        @Test
        @DisplayName("G1 · the exact maximum goes through, on either side")
        void theMaximumIsAllowed() {
            assertThatNoException().isThrownBy(() -> Entry.of(cash, ofCents(MAX_CENTS)));
            assertThatNoException().isThrownBy(() -> Entry.of(cash, ofCents(-MAX_CENTS)));
        }

        @Test
        @DisplayName("G2 · one cent more does not, on either side")
        void oneCentTooMuch() {
            assertThatExceptionOfType(AmountOutOfRangeException.class)
                    .isThrownBy(() -> Entry.of(cash, ofCents(MAX_CENTS + 1)))
                    .satisfies(thrown -> assertThat(thrown.limit()).isEqualTo(MAX_CENTS));

            assertThatExceptionOfType(AmountOutOfRangeException.class)
                    .isThrownBy(() -> Entry.of(cash, ofCents(-MAX_CENTS - 1)));
        }

        @Test
        @DisplayName("G3 · and a stored entry over the limit fails on load")
        void aTamperedRowFailsOnLoad() {
            assertThatExceptionOfType(AmountOutOfRangeException.class)
                    .isThrownBy(() -> Entry.rehydrated(cash, ofCents(Long.MAX_VALUE)));

            assertThatExceptionOfType(AmountOutOfRangeException.class)
                    .isThrownBy(() -> Entry.rehydrated(cash, ofCents(Long.MIN_VALUE)));
        }

        @Test
        @DisplayName("G4 · so a thousand maximum movements still add up instead of throwing")
        void sumsStayWithinReach() {
            Ledger ledger = new Ledger();
            for (int times = 0; times < 1_000; times++) {
                ledger.record(Transaction.record(
                        TransactionId.generate(), TODAY, "Grande", TransactionSource.APP,
                        List.of(
                                Entry.of(cash, ofCents(-MAX_CENTS)),
                                Entry.of(food, ofCents(MAX_CENTS)))));
            }

            assertThatNoException().isThrownBy(() -> ledger.balanceOf(cash, TODAY));
            assertThat(ledger.sumOfEveryEntry()).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("on a description")
    class Descriptions {

        @Test
        @DisplayName("G5 · exactly two hundred characters are kept")
        void theMaximumIsKept() {
            String longest = "a".repeat(Transaction.MAX_DESCRIPTION_LENGTH);

            assertThat(lunchDescribed(longest).description()).isEqualTo(longest);
        }

        @Test
        @DisplayName("G6 · two hundred and one are refused, and the text does not travel")
        void oneCharacterTooMany() {
            String tooLong = "a".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1);

            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> lunchDescribed(tooLong))
                    .satisfies(thrown -> {
                        assertThat(thrown.field()).isEqualTo("description");
                        assertThat(thrown.maximum())
                                .isEqualTo(Transaction.MAX_DESCRIPTION_LENGTH);
                        // Lo que vino de afuera no se repite en el mensaje: ese
                        // mensaje termina en un log o en una respuesta de la API.
                        assertThat(thrown.getMessage()).doesNotContain(tooLong);
                    });
        }

        @Test
        @DisplayName("G7 · an emoji counts as one character, not as its bytes")
        void codePointsAreWhatCount() {
            String emojis = "🍕".repeat(Transaction.MAX_DESCRIPTION_LENGTH);

            assertThat(lunchDescribed(emojis).description()).isEqualTo(emojis);

            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> lunchDescribed(emojis + "🍕"));
        }

        @Test
        @DisplayName("G8 · a line break is refused, because it forges a log line")
        void controlCharactersAreRefused() {
            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> lunchDescribed("Almuerzo\nSaldo: 0"))
                    .satisfies(thrown -> assertThat(thrown.field()).isEqualTo("description"));

            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> lunchDescribed("Almuerzo\tcolumna"));

            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> lunchDescribed("Almuerzo\u0000con NUL"));

            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> lunchDescribed("Almuerzo\u0085sigue"));
        }

        /**
         * The order matters: trimmed first, checked afterwards. A message typed
         * into a chat ends in a newline more often than not, and refusing it
         * would be refusing the message. What is left after trimming is what
         * gets stored, and that is what may not hold a control character.
         */
        @Test
        @DisplayName("G9 · blanks at the ends go away, control characters among them")
        void blanksAreTrimmed() {
            assertThat(lunchDescribed("  Almuerzo \n ").description()).isEqualTo("Almuerzo");
            assertThat(lunchDescribed("Almuerzo\u0000").description()).isEqualTo("Almuerzo");
            assertThat(lunchDescribed("   ").description()).isEmpty();
            assertThat(lunchDescribed(null).description()).isEmpty();
        }

        @Test
        @DisplayName("G10 · and a stored description over the limit fails on load")
        void aTamperedRowFailsOnLoad() {
            String tooLong = "a".repeat(Transaction.MAX_DESCRIPTION_LENGTH + 1);

            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> Transaction.rehydrated(
                            TransactionId.generate(), TODAY, tooLong, TransactionSource.APP,
                            lunchEntries(), null, null));

            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> Transaction.rehydrated(
                            TransactionId.generate(), TODAY, "Mal\nhecho", TransactionSource.APP,
                            lunchEntries(), null, null));
        }

        private List<Entry> lunchEntries() {
            return List.of(Entry.of(cash, ofUnits(-35_000)), Entry.of(food, ofUnits(35_000)));
        }

        private Transaction lunchDescribed(String description) {
            return Transaction.record(
                    TransactionId.generate(), TODAY, description, TransactionSource.APP,
                    lunchEntries());
        }
    }

    @Nested
    @DisplayName("on the reason of a cancellation")
    class Reasons {

        @Test
        @DisplayName("G11 · two hundred characters are kept and two hundred and one are refused")
        void theLimit() {
            String longest = "a".repeat(Cancellation.MAX_REASON_LENGTH);

            assertThat(new Cancellation(NOW, longest, TransactionSource.APP).reason())
                    .isEqualTo(longest);

            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> new Cancellation(
                            NOW, longest + "a", TransactionSource.APP))
                    .satisfies(thrown -> assertThat(thrown.field()).isEqualTo("reason"));
        }

        @Test
        @DisplayName("G12 · a line break is refused there too")
        void controlCharactersAreRefused() {
            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> new Cancellation(
                            NOW, "Duplicada\nAnulada por el sistema", TransactionSource.APP));
        }

        @Test
        @DisplayName("G13 · and the ledger refuses the same reason on its way in")
        void theLedgerRefusesItToo() {
            Ledger ledger = new Ledger();
            Transaction lunch = Operations.expense(
                    TransactionId.generate(), TODAY, "Almuerzo", cash, food, ofUnits(35_000));
            ledger.record(lunch);

            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> ledger.cancel(lunch.id(), NOW, "Error\nfalso"));

            assertThat(lunch.isCancelled()).isFalse();
        }
    }

    @Nested
    @DisplayName("on the name of an account")
    class Names {

        @Test
        @DisplayName("G14 · sixty characters are kept and sixty-one are refused")
        void theLimit() {
            String longest = "a".repeat(Account.MAX_NAME_LENGTH);

            assertThat(Account.open(AccountType.ASSET, longest).name()).isEqualTo(longest);

            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> Account.open(AccountType.ASSET, longest + "a"))
                    .satisfies(thrown -> assertThat(thrown.field()).isEqualTo("name"));
        }

        @Test
        @DisplayName("G15 · an emoji in a name counts as one character")
        void codePointsAreWhatCount() {
            String emojis = "🏦".repeat(Account.MAX_NAME_LENGTH);

            assertThat(Account.open(AccountType.ASSET, emojis).name()).isEqualTo(emojis);

            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> Account.open(AccountType.ASSET, emojis + "🏦"));
        }

        @Test
        @DisplayName("G16 · control characters are refused, blanks trimmed, blank-only rejected")
        void whatANameMayNotBe() {
            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> Account.open(AccountType.ASSET, "Banco\nFalso"));

            assertThat(Account.open(AccountType.ASSET, "  Bancolombia  ").name())
                    .isEqualTo("Bancolombia");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Account.open(AccountType.ASSET, "   "));
        }

        @Test
        @DisplayName("G17 · renaming is held to the same limits")
        void renamingIsCheckedToo() {
            Chart chart = new Chart();
            Account bank = chart.open(AccountType.ASSET, "Bancolombia");

            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> chart.rename(
                            bank.id(), "a".repeat(Account.MAX_NAME_LENGTH + 1)));

            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> chart.rename(bank.id(), "Banco\nFalso"));

            assertThat(bank.name()).isEqualTo("Bancolombia");
        }

        @Test
        @DisplayName("G18 · and a stored name over the limit fails on load")
        void aTamperedRowFailsOnLoad() {
            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> Account.rehydrated(
                            AccountId.generate(), AccountType.ASSET,
                            "a".repeat(Account.MAX_NAME_LENGTH + 1), null));

            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> Account.rehydrated(
                            AccountId.generate(), AccountType.ASSET, "Banco\u0000Falso", null));
        }
    }
}

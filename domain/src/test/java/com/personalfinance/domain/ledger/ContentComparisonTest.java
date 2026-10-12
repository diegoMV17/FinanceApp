package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Block G, second half: "is this the same thing I was already told?".
 *
 * <p>Ids come from the client, so the same id can arrive twice: the phone
 * retried after a timeout. Equality cannot answer that question, because two
 * transactions are equal when their ids match and that is exactly the case
 * under suspicion. This is the comparison that tells a harmless retry from a
 * contradiction, and the use cases decide what to do with the answer.
 */
@DisplayName("Comparing content")
class ContentComparisonTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 7);
    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");

    private final Account cash = TestAccounts.cash();
    private final Account food = TestAccounts.food();
    private final Account bancolombia = TestAccounts.bancolombia();

    @Nested
    @DisplayName("two transactions")
    class Transactions {

        private final TransactionId id = TransactionId.generate();

        @Test
        @DisplayName("G19 · match when every stated field matches")
        void sameContent() {
            Transaction one = lunch(id, TODAY, "Almuerzo", 35_000);
            Transaction other = lunch(id, TODAY, "Almuerzo", 35_000);

            assertThat(one.sameContentAs(other)).isTrue();
            assertThat(one.sameContentAs(one)).isTrue();
        }

        @Test
        @DisplayName("G20 · differ when the id, the date, the description or the amount differ")
        void differentContent() {
            Transaction lunch = lunch(id, TODAY, "Almuerzo", 35_000);

            assertThat(lunch.sameContentAs(lunch(TransactionId.generate(), TODAY, "Almuerzo",
                    35_000))).isFalse();
            assertThat(lunch.sameContentAs(lunch(id, TOMORROW, "Almuerzo", 35_000))).isFalse();
            assertThat(lunch.sameContentAs(lunch(id, TODAY, "Cena", 35_000))).isFalse();
            assertThat(lunch.sameContentAs(lunch(id, TODAY, "Almuerzo", 40_000))).isFalse();
        }

        @Test
        @DisplayName("G21 · differ when the entries changed account, or only their order")
        void entriesAreComparedInOrder() {
            Transaction lunch = lunch(id, TODAY, "Almuerzo", 35_000);

            Transaction fromAnotherAccount = Transaction.record(
                    id, TODAY, "Almuerzo", TransactionSource.APP, List.of(
                            Entry.of(bancolombia, ofUnits(-35_000)),
                            Entry.of(food, ofUnits(35_000))));

            Transaction theOtherWayRound = Transaction.record(
                    id, TODAY, "Almuerzo", TransactionSource.APP, List.of(
                            Entry.of(food, ofUnits(35_000)),
                            Entry.of(cash, ofUnits(-35_000))));

            assertThat(lunch.sameContentAs(fromAnotherAccount)).isFalse();
            assertThat(lunch.sameContentAs(theOtherWayRound)).isFalse();
        }

        @Test
        @DisplayName("G22 · differ when they came from somewhere else")
        void theSourceCounts() {
            Transaction fromTheApp = lunch(id, TODAY, "Almuerzo", 35_000);

            Transaction fromTheBot = Transaction.record(
                    id, TODAY, "Almuerzo", TransactionSource.BOT, List.of(
                            Entry.of(cash, ofUnits(-35_000)),
                            Entry.of(food, ofUnits(35_000))));

            assertThat(fromTheApp.sameContentAs(fromTheBot)).isFalse();
        }

        @Test
        @DisplayName("G23 · still match once one of them has been cancelled or replaced")
        void cancellationAndReplacementAreNotContent() {
            Ledger ledger = new Ledger();
            Transaction lunch = lunch(id, TODAY, "Almuerzo", 35_000);
            ledger.record(lunch);
            Transaction asStored = lunch(id, TODAY, "Almuerzo", 35_000);

            ledger.cancel(lunch.id(), NOW, "Duplicado");

            assertThat(lunch.sameContentAs(asStored)).isTrue();

            Transaction correction = Transaction.rehydrated(
                    id, TODAY, "Almuerzo", TransactionSource.APP,
                    lunch.entries(), TransactionId.generate(), null);

            // Lo que reemplaza se revisa aparte, en el caso de uso: una
            // corrección con el mismo contenido no es el mismo movimiento.
            assertThat(correction.sameContentAs(asStored)).isTrue();
            assertThat(correction.replaces()).isPresent();
        }

        private Transaction lunch(
                TransactionId id, LocalDate date, String description, long amount) {
            return Operations.expense(id, date, description, cash, food, ofUnits(amount));
        }
    }

    @Nested
    @DisplayName("two accounts")
    class Accounts {

        private final AccountId id = AccountId.generate();

        @Test
        @DisplayName("G24 · match on id, type and name, ignoring case and blanks")
        void sameContent() {
            Account account = Account.open(id, AccountType.ASSET, "Bancolombia");

            assertThat(account.sameContentAs(
                    Account.open(id, AccountType.ASSET, "Bancolombia"))).isTrue();
            assertThat(account.sameContentAs(
                    Account.open(id, AccountType.ASSET, "  bancolombia "))).isTrue();
        }

        @Test
        @DisplayName("G25 · differ on id, type or a real change of name")
        void differentContent() {
            Account account = Account.open(id, AccountType.ASSET, "Bancolombia");

            assertThat(account.sameContentAs(
                    Account.open(AccountId.generate(), AccountType.ASSET, "Bancolombia")))
                    .isFalse();
            assertThat(account.sameContentAs(
                    Account.open(id, AccountType.EXPENSE, "Bancolombia"))).isFalse();
            assertThat(account.sameContentAs(
                    Account.open(id, AccountType.ASSET, "Bancolombia Ahorros"))).isFalse();
        }

        @Test
        @DisplayName("G26 · still match once one of them has been archived")
        void archivingIsNotContent() {
            Account open = Account.open(id, AccountType.EXPENSE, "Alimentación");
            Account archived = Account.rehydrated(id, AccountType.EXPENSE, "Alimentación", NOW);

            assertThat(open.sameContentAs(archived)).isTrue();
            assertThat(archived.sameContentAs(open)).isTrue();
        }

        @Test
        @DisplayName("G27 · and an account says whether it answers to a name")
        void answersToItsName() {
            Account account = Account.open(id, AccountType.ASSET, "Bancolombia");

            assertThat(account.answersTo("  BANCOLOMBIA ")).isTrue();
            assertThat(account.answersTo("Bancolombia Ahorros")).isFalse();
        }

        /**
         * Two different questions, and mixing them up loses a rename. "Does it
         * answer to this name?" decides whether two accounts would be
         * indistinguishable in a selector, and ignores case on purpose. "Is it
         * called exactly this?" decides whether a rename has anything to do,
         * and fixing the capitalisation of a name is something to do.
         */
        @Test
        @DisplayName("G28 · and whether it is called exactly that, capitals included")
        void isNamedExactly() {
            Account account = Account.open(id, AccountType.ASSET, "Nequi");

            assertThat(account.isNamed("Nequi")).isTrue();
            assertThat(account.isNamed("  Nequi  ")).isTrue();
            assertThat(account.isNamed("nequi")).isFalse();
            assertThat(account.isNamed("NEQUI")).isFalse();
            assertThat(account.isNamed("Nequi Ahorros")).isFalse();
        }
    }
}

package com.personalfinance.application;

import static com.personalfinance.application.Outcome.Status.ALREADY_APPLIED;
import static com.personalfinance.application.Outcome.Status.APPLIED;
import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;



import com.personalfinance.application.command.AmendTransactionCommand;
import com.personalfinance.application.command.ArchiveAccountCommand;
import com.personalfinance.application.command.ArchivePersonCommand;
import com.personalfinance.application.command.BorrowCommand;
import com.personalfinance.application.command.CancelTransactionCommand;
import com.personalfinance.application.command.ExpenseCommand;
import com.personalfinance.application.command.LinkAccountToPersonCommand;
import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.application.command.OpenAccountCommand;
import com.personalfinance.application.command.OpenPersonCommand;
import com.personalfinance.application.command.OpeningBalanceCommand;
import com.personalfinance.application.command.ReceiveThirdPartyMoneyCommand;
import com.personalfinance.application.command.RenameAccountCommand;
import com.personalfinance.application.command.RenamePersonCommand;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.application.read.BookSummary;
import com.personalfinance.application.command.SetBudgetCommand;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.AmendTransaction;
import com.personalfinance.application.usecase.ArchiveAccount;
import com.personalfinance.application.usecase.ArchivePerson;
import com.personalfinance.application.usecase.CancelTransaction;
import com.personalfinance.application.usecase.LinkAccountToPerson;
import com.personalfinance.application.usecase.OpenAccount;
import com.personalfinance.application.usecase.OpenPerson;
import com.personalfinance.application.usecase.RecordMovement;
import com.personalfinance.application.usecase.SetBudget;
import com.personalfinance.application.usecase.RenameAccount;
import com.personalfinance.application.usecase.RenamePerson;
import com.personalfinance.domain.ledger.Budget;
import com.personalfinance.domain.ledger.BudgetId;
import com.personalfinance.domain.ledger.DuplicateBudgetException;
import com.personalfinance.domain.ledger.UnexpectedAccountTypeException;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountCannotBelongToAPersonException;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountStillHoldsMoneyException;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.AmountOutOfRangeException;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.ControlCharactersException;
import com.personalfinance.domain.ledger.DuplicateAccountException;
import com.personalfinance.domain.ledger.DuplicateAccountNameException;
import com.personalfinance.domain.ledger.DuplicatePersonException;
import com.personalfinance.domain.ledger.DuplicatePersonNameException;
import com.personalfinance.domain.ledger.DuplicateTransactionException;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.PersonStillHasOpenAccountsException;
import com.personalfinance.domain.ledger.TextTooLongException;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionAlreadyCancelledException;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.UnknownAccountException;
import com.personalfinance.domain.shared.Money;
import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los casos de uso contra Postgres de verdad.
 *
 * <p>Lo que se prueba aquí no es que el dominio sepa sumar — eso ya está
 * probado en milisegundos — sino lo que solo existe en esta capa: que un
 * reintento idéntico no escriba nada, que el mismo id con otro contenido falle,
 * que una excepción deje la base como estaba, y que cada cambio que ocurrió
 * deje exactamente una línea de bitácora.
 *
 * <p>{@code NOT_SUPPORTED} por lo mismo que en los demás tests de esta capa:
 * los casos de uso abren su propia transacción y el trigger de balance es
 * diferido. Envueltos en una transacción de test que nunca hace commit, no se
 * probaría nada de eso.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Los casos de uso")
class WriteUseCasesTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Autowired
    private RecordMovement recordMovement;

    @Autowired
    private CancelTransaction cancelTransaction;

    @Autowired
    private AmendTransaction amendTransaction;

    @Autowired
    private OpenAccount openAccount;

    @Autowired
    private ArchiveAccount archiveAccount;

    @Autowired
    private RenameAccount renameAccount;

    @Autowired
    private OpenPerson openPerson;

    @Autowired
    private RenamePerson renamePerson;

    @Autowired
    private ArchivePerson archivePerson;

    @Autowired
    private LinkAccountToPerson linkAccountToPerson;

    @Autowired
    private SetBudget setBudget;

    @Autowired
    private ReadBook readBook;

    @Autowired
    private BookLock lock;

    @Autowired
    private BookLoader loader;

    @Autowired
    private JdbcTemplate jdbc;

    private AccountId bank;
    private AccountId cash;
    private AccountId food;
    private AccountId equity;
    private AccountId brother;
    private AccountId momsFund;

    @BeforeEach
    void startFromAnEmptyBook() {
        jdbc.execute(
                "TRUNCATE entries, transactions, audit_log, budgets, accounts, people CASCADE");

        bank = open(AccountType.ASSET, "Bancolombia");
        cash = open(AccountType.ASSET, "Efectivo");
        food = open(AccountType.EXPENSE, "Alimentación");
        equity = open(AccountType.EQUITY, "Saldos iniciales");
        brother = open(AccountType.PAYABLE, "Hermano");
        momsFund = open(AccountType.THIRD_PARTY_FUND, "Mamá");
    }

    @Nested
    @DisplayName("al registrar un movimiento")
    class Recording {

        @Test
        @DisplayName("U1 · lo aplican, mueven el saldo y dejan una línea de bitácora")
        void recordsAndAudits() {
            startCashWith(100_000);
            TransactionId id = TransactionId.generate();

            Outcome<Transaction> outcome = recordMovement.execute(lunch(id, 35_000));

            assertThat(outcome.status()).isEqualTo(APPLIED);
            assertThat(outcome.value().id()).isEqualTo(id);
            assertThat(balanceOf(cash)).isEqualTo(ofUnits(65_000));
            assertThat(balanceOf(food)).isEqualTo(ofUnits(35_000));
            assertThat(auditFor(id.value()))
                    .extracting("entity", "action", "source")
                    .containsExactly(tuple("transaction", "CREATE", "APP"));
        }

        /**
         * El caso de la red: el teléfono envía, se corta antes de la respuesta
         * y vuelve a enviar lo mismo. Sin esto, el saldo quedaría al doble.
         */
        @Test
        @DisplayName("U2 · el mismo comando dos veces cuenta una sola vez y no audita de nuevo")
        void theSameCommandTwice() {
            startCashWith(100_000);
            MovementCommand lunch = lunch(TransactionId.generate(), 35_000);

            assertThat(recordMovement.execute(lunch).status()).isEqualTo(APPLIED);
            Outcome<Transaction> again = recordMovement.execute(lunch);

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(again.value().id()).isEqualTo(lunch.id());
            assertThat(balanceOf(cash)).isEqualTo(ofUnits(65_000));
            assertThat(auditFor(lunch.id().value())).hasSize(1);
        }

        @Test
        @DisplayName("U3 · el mismo id con otro monto es un conflicto y no cambia nada")
        void theSameIdWithOtherContent() {
            startCashWith(100_000);
            TransactionId id = TransactionId.generate();
            recordMovement.execute(lunch(id, 35_000));

            assertThatExceptionOfType(DuplicateTransactionException.class)
                    .isThrownBy(() -> recordMovement.execute(lunch(id, 40_000)));

            assertThat(balanceOf(cash)).isEqualTo(ofUnits(65_000));
            assertThat(auditFor(id.value())).hasSize(1);
        }

        @Test
        @DisplayName("U11 · un monto o un texto fuera de límite se rechaza sin tocar la base")
        void uglyInputIsRefused() {
            startCashWith(100_000);
            long transactionsBefore = transactionCount();
            long auditBefore = auditCount();

            assertThatExceptionOfType(AmountOutOfRangeException.class)
                    .isThrownBy(() -> recordMovement.execute(new ExpenseCommand(
                            TransactionId.generate(), TODAY, "Enorme", cash, food,
                            Money.ofCents(1_000_000_000_001L))));

            assertThatExceptionOfType(TextTooLongException.class)
                    .isThrownBy(() -> recordMovement.execute(new ExpenseCommand(
                            TransactionId.generate(), TODAY, "a".repeat(201), cash, food,
                            ofUnits(1_000))));

            assertThatExceptionOfType(ControlCharactersException.class)
                    .isThrownBy(() -> recordMovement.execute(new ExpenseCommand(
                            TransactionId.generate(), TODAY, "Almuerzo\nSaldo: 0", cash, food,
                            ofUnits(1_000))));

            assertThat(transactionCount()).isEqualTo(transactionsBefore);
            assertThat(auditCount()).isEqualTo(auditBefore);
        }

        /**
         * La excepción salta después de tomar el candado y cargar el libro, que
         * es justo donde importa: la transacción se deshace y el turno se
         * suelta, así que lo siguiente que llegue puede escribir.
         */
        @Test
        @DisplayName("U10 · una excepción del dominio no deja rastro, y suelta el turno")
        void aFailureLeavesNothingBehind() {
            startCashWith(100_000);
            long transactionsBefore = transactionCount();
            long auditBefore = auditCount();

            assertThatExceptionOfType(UnknownAccountException.class)
                    .isThrownBy(() -> recordMovement.execute(new ExpenseCommand(
                            TransactionId.generate(), TODAY, "Fantasma",
                            AccountId.generate(), food, ofUnits(10_000))));

            assertThat(transactionCount()).isEqualTo(transactionsBefore);
            assertThat(auditCount()).isEqualTo(auditBefore);

            assertThat(recordMovement.execute(lunch(TransactionId.generate(), 1_000)).status())
                    .isEqualTo(APPLIED);
        }
    }

    @Nested
    @DisplayName("al anular")
    class Cancelling {

        @Test
        @DisplayName("U4 · anulan una vez, reconocen el reintento y conservan el primer motivo")
        void cancelOnceAndRecogniseTheRetry() {
            startCashWith(100_000);
            TransactionId id = TransactionId.generate();
            recordMovement.execute(lunch(id, 35_000));

            Outcome<Cancellation> first = cancelTransaction.execute(
                    new CancelTransactionCommand(id, "Lo registré dos veces"));

            assertThat(first.status()).isEqualTo(APPLIED);
            assertThat(balanceOf(cash)).isEqualTo(ofUnits(100_000));

            Outcome<Cancellation> again = cancelTransaction.execute(
                    new CancelTransactionCommand(id, "  Lo registré dos veces "));

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(again.value()).isEqualTo(first.value());

            assertThatExceptionOfType(TransactionAlreadyCancelledException.class)
                    .isThrownBy(() -> cancelTransaction.execute(
                            new CancelTransactionCommand(id, "Otro motivo")));

            assertThat(storedCancellationNote(id)).isEqualTo("Lo registré dos veces");
            assertThat(auditFor(id.value()))
                    .extracting("action")
                    .containsExactly("CREATE", "CANCEL");
        }
    }

    @Nested
    @DisplayName("al corregir")
    class Amending {

        @Test
        @DisplayName("U5 · corrigen en un commit, reconocen el reintento y rechazan lo demás")
        void amendOnceAndRecogniseTheRetry() {
            startCashWith(100_000);
            TransactionId original = TransactionId.generate();
            recordMovement.execute(lunch(original, 35_000));

            MovementCommand correction = lunch(TransactionId.generate(), 40_000);
            Outcome<Transaction> amended = amendTransaction.execute(
                    new AmendTransactionCommand(original, correction));

            assertThat(amended.status()).isEqualTo(APPLIED);
            assertThat(amended.value().id()).isEqualTo(correction.id());
            assertThat(amended.value().replaces()).contains(original);
            assertThat(balanceOf(cash)).isEqualTo(ofUnits(60_000));

            Outcome<Transaction> again = amendTransaction.execute(
                    new AmendTransactionCommand(original, correction));

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(balanceOf(cash)).isEqualTo(ofUnits(60_000));
            assertThat(auditFor(original.value()))
                    .extracting("action")
                    .containsExactly("CREATE", "AMEND");

            // Y una corrección nueva sobre la original, que ya no cuenta.
            assertThatExceptionOfType(TransactionAlreadyCancelledException.class)
                    .isThrownBy(() -> amendTransaction.execute(new AmendTransactionCommand(
                            original, lunch(TransactionId.generate(), 50_000))));
        }

        @Test
        @DisplayName("U5b · la bitácora de una corrección dice cuál la reemplazó")
        void theAuditSaysWhatReplacedIt() {
            startCashWith(100_000);
            TransactionId original = TransactionId.generate();
            recordMovement.execute(lunch(original, 35_000));
            MovementCommand correction = lunch(TransactionId.generate(), 40_000);

            amendTransaction.execute(new AmendTransactionCommand(original, correction));

            Map<String, Object> amend = auditFor(original.value()).get(1);
            assertThat(String.valueOf(amend.get("after"))).contains(correction.id().toString());
            assertThat(amend.get("before")).isNull();
        }
    }

    @Nested
    @DisplayName("con las cuentas")
    class Accounts {

        @Test
        @DisplayName("U7 · abren una vez; mismo id otro nombre y mismo nombre otro id, no")
        void openingAnAccount() {
            AccountId id = AccountId.generate();
            OpenAccountCommand nequi = new OpenAccountCommand(id, AccountType.ASSET, "Nequi");

            assertThat(openAccount.execute(nequi).status()).isEqualTo(APPLIED);
            assertThat(openAccount.execute(nequi).status()).isEqualTo(ALREADY_APPLIED);

            assertThatExceptionOfType(DuplicateAccountException.class)
                    .isThrownBy(() -> openAccount.execute(new OpenAccountCommand(
                            id, AccountType.ASSET, "Daviplata")));

            assertThatExceptionOfType(DuplicateAccountNameException.class)
                    .isThrownBy(() -> openAccount.execute(new OpenAccountCommand(
                            AccountId.generate(), AccountType.ASSET, "  nequi ")));

            assertThat(auditFor(id.value())).hasSize(1);
            assertThat(nameOf(id)).isEqualTo("Nequi");
        }

        @Test
        @DisplayName("U8 · archivan solo la cuenta vacía, y el reintento devuelve la fecha original")
        void archivingAnAccount() {
            startCashWith(100_000);

            assertThatExceptionOfType(AccountStillHoldsMoneyException.class)
                    .isThrownBy(() -> archiveAccount.execute(new ArchiveAccountCommand(cash)));
            assertThat(archivedAt(cash)).isNull();
            assertThat(auditFor(cash.value())).hasSize(1);

            recordMovement.execute(lunch(TransactionId.generate(), 100_000));

            Outcome<Account> archived =
                    archiveAccount.execute(new ArchiveAccountCommand(cash));
            assertThat(archived.status()).isEqualTo(APPLIED);
            assertThat(archivedAt(cash)).isNotNull();

            Outcome<Account> again = archiveAccount.execute(new ArchiveAccountCommand(cash));
            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(again.value().archivedAt())
                    .contains(archived.value().archivedAt().orElseThrow());
            assertThat(auditFor(cash.value()))
                    .extracting("action")
                    .containsExactly("CREATE", "ARCHIVE");
        }

        @Test
        @DisplayName("U9 · renombran guardando el nombre anterior, y el mismo nombre no audita")
        void renamingAnAccount() {
            Outcome<Account> renamed = renameAccount.execute(
                    new RenameAccountCommand(bank, "Bancolombia Ahorros"));

            assertThat(renamed.status()).isEqualTo(APPLIED);
            assertThat(nameOf(bank)).isEqualTo("Bancolombia Ahorros");

            Map<String, Object> audit = auditFor(bank.value()).get(1);
            assertThat(audit.get("action")).isEqualTo("RENAME");
            assertThat(String.valueOf(audit.get("before"))).contains("Bancolombia");
            assertThat(String.valueOf(audit.get("after"))).contains("Bancolombia Ahorros");

            Outcome<Account> again = renameAccount.execute(
                    new RenameAccountCommand(bank, "  Bancolombia Ahorros  "));

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(nameOf(bank)).isEqualTo("Bancolombia Ahorros");
            assertThat(auditFor(bank.value())).hasSize(2);
        }

        /**
         * Arreglar las mayúsculas de un nombre es un cambio que la persona
         * pidió y que se ve en pantalla. Comparar con la regla de nombres
         * duplicados — que ignora mayúsculas, porque sirve para otra cosa — lo
         * convertía en un "ya aplicado" y el nombre se quedaba como estaba.
         */
        @Test
        @DisplayName("U9b · cambiar solo las mayúsculas sí es un cambio, y se aplica")
        void renamingOnlyTheCapitals() {
            AccountId nequi = open(AccountType.ASSET, "nequi");

            Outcome<Account> renamed = renameAccount.execute(
                    new RenameAccountCommand(nequi, "Nequi"));

            assertThat(renamed.status()).isEqualTo(APPLIED);
            assertThat(renamed.value().name()).isEqualTo("Nequi");
            assertThat(nameOf(nequi)).isEqualTo("Nequi");

            Map<String, Object> audit = auditFor(nequi.value()).get(1);
            assertThat(audit.get("action")).isEqualTo("RENAME");
            assertThat(String.valueOf(audit.get("before"))).contains("\"nequi\"");
            assertThat(String.valueOf(audit.get("after"))).contains("\"Nequi\"");
            assertThat(auditFor(nequi.value())).hasSize(2);
        }
    }

    /**
     * El incremento 2. Lo que se prueba aquí es lo de esta capa: que un
     * reintento no escriba, que mover el vínculo deje en la bitácora de quién
     * era antes, y que una regla del dominio no deje rastro al fallar.
     */
    @Nested
    @DisplayName("con las personas")
    class People {

        @Test
        @DisplayName("U15 · abrir una la aplica y deja una línea de bitácora")
        void opensAndAudits() {
            PersonId id = PersonId.generate();

            Outcome<Person> outcome = openPerson.execute(new OpenPersonCommand(id, "  Juan  "));

            assertThat(outcome.status()).isEqualTo(APPLIED);
            assertThat(outcome.value().name()).isEqualTo("Juan");
            assertThat(nameOfPerson(id)).isEqualTo("Juan");
            assertThat(auditFor(id.value()))
                    .extracting("entity", "action", "source")
                    .containsExactly(tuple("person", "CREATE", "APP"));
        }

        @Test
        @DisplayName("U16 · el mismo id con el mismo nombre es un reintento, sin bitácora")
        void openingTwiceWritesOnce() {
            PersonId id = PersonId.generate();
            openPerson.execute(new OpenPersonCommand(id, "Juan"));
            long auditBefore = auditCount();

            Outcome<Person> again = openPerson.execute(new OpenPersonCommand(id, " juan "));

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(auditCount()).isEqualTo(auditBefore);
            assertThat(peopleCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("U17 · el mismo id con otro nombre es una contradicción")
        void sameIdOtherContentConflicts() {
            PersonId id = PersonId.generate();
            openPerson.execute(new OpenPersonCommand(id, "Juan"));

            assertThatExceptionOfType(DuplicatePersonException.class)
                    .isThrownBy(() -> openPerson.execute(new OpenPersonCommand(id, "Pedro")));

            assertThat(nameOfPerson(id)).isEqualTo("Juan");
        }

        @Test
        @DisplayName("U18 · y dos personas abiertas no se llaman igual")
        void noDuplicateNames() {
            openPerson.execute(new OpenPersonCommand(PersonId.generate(), "Juan"));

            assertThatExceptionOfType(DuplicatePersonNameException.class)
                    .isThrownBy(() -> openPerson.execute(
                            new OpenPersonCommand(PersonId.generate(), "  juan ")));

            assertThat(peopleCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("U19 · renombrar deja el nombre anterior en la bitácora")
        void renamingKeepsTheOldName() {
            PersonId id = addPerson("Juan");

            Outcome<Person> renamed = renamePerson.execute(
                    new RenamePersonCommand(id, "Juan Pérez"));

            assertThat(renamed.status()).isEqualTo(APPLIED);
            assertThat(nameOfPerson(id)).isEqualTo("Juan Pérez");
            assertThat(auditFor(id.value()))
                    .extracting("action", "before", "after")
                    .containsExactly(
                            tuple("CREATE", null, null),
                            tuple("RENAME", "{\"name\": \"Juan\"}",
                                    "{\"name\": \"Juan Pérez\"}"));
        }

        @Test
        @DisplayName("U20 · pedir el nombre exacto que ya tiene no escribe nada")
        void renamingToTheSameNameWritesNothing() {
            PersonId id = addPerson("Juan");
            long auditBefore = auditCount();

            Outcome<Person> again = renamePerson.execute(
                    new RenamePersonCommand(id, "  Juan  "));

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(auditCount()).isEqualTo(auditBefore);
        }

        @Test
        @DisplayName("U21 · vincular una cuenta la deja de esa persona, y lo anota")
        void linksAndAudits() {
            PersonId juan = addPerson("Juan");
            AccountId owedByJuan = open(AccountType.RECEIVABLE, "Juan");

            Outcome<Account> linked = linkAccountToPerson.execute(
                    new LinkAccountToPersonCommand(owedByJuan, juan));

            assertThat(linked.status()).isEqualTo(APPLIED);
            assertThat(personOf(owedByJuan)).isEqualTo(juan.value().toString());
            assertThat(auditFor(owedByJuan.value()))
                    .extracting("entity", "action", "before", "after")
                    .containsExactly(
                            tuple("account", "CREATE", null, null),
                            tuple("account", "LINK", null,
                                    "{\"person\": \"%s\"}".formatted(juan)));
        }

        @Test
        @DisplayName("U22 · vincularla a quien ya es suya es un reintento, sin bitácora")
        void linkingTwiceWritesOnce() {
            PersonId juan = addPerson("Juan");
            AccountId owedByJuan = open(AccountType.RECEIVABLE, "Juan");
            linkAccountToPerson.execute(new LinkAccountToPersonCommand(owedByJuan, juan));
            long auditBefore = auditCount();

            Outcome<Account> again = linkAccountToPerson.execute(
                    new LinkAccountToPersonCommand(owedByJuan, juan));

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(auditCount()).isEqualTo(auditBefore);
        }

        /**
         * Mover el vínculo no es una contradicción: es la forma de corregir un
         * dedazo sin archivar la cuenta. Lo que no se puede perder es de quién
         * era, porque la tabla solo conserva de quién es ahora.
         */
        @Test
        @DisplayName("U23 · moverla a otra persona se aplica y deja dicho de quién era")
        void movingTheLinkKeepsWhoseItWas() {
            PersonId juan = addPerson("Juan");
            PersonId pedro = addPerson("Pedro");
            AccountId owed = open(AccountType.RECEIVABLE, "El del taller");
            linkAccountToPerson.execute(new LinkAccountToPersonCommand(owed, juan));

            Outcome<Account> moved = linkAccountToPerson.execute(
                    new LinkAccountToPersonCommand(owed, pedro));

            assertThat(moved.status()).isEqualTo(APPLIED);
            assertThat(personOf(owed)).isEqualTo(pedro.value().toString());
            assertThat(auditFor(owed.value()))
                    .extracting("action", "before", "after")
                    .containsExactly(
                            tuple("CREATE", null, null),
                            tuple("LINK", null, "{\"person\": \"%s\"}".formatted(juan)),
                            tuple("LINK", "{\"person\": \"%s\"}".formatted(juan),
                                    "{\"person\": \"%s\"}".formatted(pedro)));
        }

        @Test
        @DisplayName("U24 · vincular una categoría falla y no deja rastro")
        void aCategoryIsNobody() {
            PersonId juan = addPerson("Juan");
            long auditBefore = auditCount();

            assertThatExceptionOfType(AccountCannotBelongToAPersonException.class)
                    .isThrownBy(() -> linkAccountToPerson.execute(
                            new LinkAccountToPersonCommand(food, juan)));

            assertThat(personOf(food)).isNull();
            assertThat(auditCount()).isEqualTo(auditBefore);
        }

        @Test
        @DisplayName("U25 · archivar exige que sus cuentas estén archivadas")
        void archivingNeedsEverythingClosed() {
            PersonId juan = addPerson("Juan");
            AccountId owedByJuan = open(AccountType.RECEIVABLE, "Juan");
            linkAccountToPerson.execute(new LinkAccountToPersonCommand(owedByJuan, juan));

            assertThatExceptionOfType(PersonStillHasOpenAccountsException.class)
                    .isThrownBy(() -> archivePerson.execute(new ArchivePersonCommand(juan)));

            assertThat(archivedAtOfPerson(juan)).isNull();
        }

        @Test
        @DisplayName("U26 · y una vez archivada, archivarla otra vez es un reintento")
        void archivingTwiceWritesOnce() {
            PersonId juan = addPerson("Juan");
            AccountId owedByJuan = open(AccountType.RECEIVABLE, "Juan");
            linkAccountToPerson.execute(new LinkAccountToPersonCommand(owedByJuan, juan));
            archiveAccount.execute(new ArchiveAccountCommand(owedByJuan));

            Outcome<Person> archived = archivePerson.execute(new ArchivePersonCommand(juan));
            long auditBefore = auditCount();
            Outcome<Person> again = archivePerson.execute(new ArchivePersonCommand(juan));

            assertThat(archived.status()).isEqualTo(APPLIED);
            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(again.value().archivedAt()).isEqualTo(archived.value().archivedAt());
            assertThat(archivedAtOfPerson(juan)).isNotNull();
            assertThat(auditCount()).isEqualTo(auditBefore);
        }
    }

    @Nested
    @DisplayName("al leer")
    class Reading {

        /** HU22. El banco dice 800.000 y solo 500.000 son suyos. */
        @Test
        @DisplayName("U12 · el resumen descuenta la plata que solo pasa por sus manos")
        void theSummaryTellsWhatIsReallyYours() {
            recordMovement.execute(new OpeningBalanceCommand(
                    TransactionId.generate(), TODAY, bank, equity, ofUnits(500_000)));
            recordMovement.execute(new ReceiveThirdPartyMoneyCommand(
                    TransactionId.generate(), TODAY, "Fondo de mamá", momsFund, bank,
                    ofUnits(100_000)));
            recordMovement.execute(new BorrowCommand(
                    TransactionId.generate(), TODAY, "Me prestó", brother, bank,
                    ofUnits(200_000)));

            BookSummary summary = readBook.summary(TODAY);

            assertThat(balanceOf(bank)).isEqualTo(ofUnits(800_000));
            assertThat(summary.moneyReallyMine()).isEqualTo(ofUnits(500_000));
            assertThat(summary.totalFor(AccountType.THIRD_PARTY_FUND))
                    .isEqualTo(ofUnits(100_000));
            assertThat(summary.totalFor(AccountType.PAYABLE)).isEqualTo(ofUnits(200_000));
            assertThat(summary.asOf()).isEqualTo(TODAY);
        }
    }

    @Nested
    @DisplayName("fuera de una transacción")
    class WithoutATransaction {

        /**
         * Un {@code pg_advisory_xact_lock} en autocommit se suelta al terminar
         * la sentencia que lo pide. Fallar aquí es la diferencia entre no tener
         * candado y creer que se tiene uno.
         */
        @Test
        @DisplayName("U13 · el candado se niega a tomarse")
        void theLockRefuses() {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> lock.acquire());
        }

        @Test
        @DisplayName("U14 · y el libro se niega a cargarse")
        void theLoaderRefuses() {
            assertThatExceptionOfType(IllegalTransactionStateException.class)
                    .isThrownBy(() -> loader.load());
        }
    }

    // --- Ayudas.

    /** U27 a U32. HU26: poner el límite de una categoría para un mes. */
    @Nested
    @DisplayName("al poner un presupuesto")
    class Budgeting {

        private static final YearMonth MARCH = YearMonth.of(2026, 3);

        @Test
        @DisplayName("U27 · lo guarda y deja una línea de bitácora")
        void setsAndAudits() {
            BudgetId id = BudgetId.generate();

            Outcome<Budget> outcome = setBudget.execute(
                    new SetBudgetCommand(id, food, MARCH, ofUnits(500_000)));

            assertThat(outcome.status()).isEqualTo(APPLIED);
            assertThat(outcome.value().limit()).isEqualTo(ofUnits(500_000));
            assertThat(liveLimitOf(food, MARCH)).isEqualTo(50_000_000L);
            assertThat(auditFor(id.value()))
                    .extracting("entity", "action", "before", "after")
                    .containsExactly(tuple("budget", "CREATE", null, null));
        }

        @Test
        @DisplayName("U28 · el mismo id con el mismo monto es un reintento, sin bitácora")
        void theSameIdTwiceWritesOnce() {
            BudgetId id = BudgetId.generate();
            SetBudgetCommand command =
                    new SetBudgetCommand(id, food, MARCH, ofUnits(500_000));
            setBudget.execute(command);
            long auditBefore = auditCount();

            Outcome<Budget> again = setBudget.execute(command);

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(auditCount()).isEqualTo(auditBefore);
            assertThat(budgetCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("U29 · el mismo id con otro monto es una contradicción")
        void theSameIdWithAnotherLimitConflicts() {
            BudgetId id = BudgetId.generate();
            setBudget.execute(new SetBudgetCommand(id, food, MARCH, ofUnits(500_000)));

            assertThatExceptionOfType(DuplicateBudgetException.class).isThrownBy(
                    () -> setBudget.execute(
                            new SetBudgetCommand(id, food, MARCH, ofUnits(600_000))));

            assertThat(liveLimitOf(food, MARCH)).isEqualTo(50_000_000L);
        }

        /**
         * La otra cara de la idempotencia, la del hecho y no la del id: si el
         * límite vivo ya dice ese monto, repetirlo con un id nuevo no es un
         * cambio de idea. Sin esto, un cliente que reintenta generando otro id
         * iría encadenando límites idénticos y la historia del mes se llenaría
         * de cambios que nunca ocurrieron.
         */
        @Test
        @DisplayName("U30 · el mismo monto con otro id tampoco escribe: el hecho ya estaba")
        void theSameLimitUnderAnotherIdWritesNothing() {
            setBudget.execute(new SetBudgetCommand(
                    BudgetId.generate(), food, MARCH, ofUnits(500_000)));
            long auditBefore = auditCount();

            Outcome<Budget> again = setBudget.execute(new SetBudgetCommand(
                    BudgetId.generate(), food, MARCH, ofUnits(500_000)));

            assertThat(again.status()).isEqualTo(ALREADY_APPLIED);
            assertThat(auditCount()).isEqualTo(auditBefore);
            assertThat(budgetCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("U31 · otro monto reemplaza el anterior, lo marca y anota cuál era")
        void anotherLimitReplacesThePreviousOne() {
            BudgetId first = BudgetId.generate();
            BudgetId second = BudgetId.generate();
            setBudget.execute(new SetBudgetCommand(first, food, MARCH, ofUnits(500_000)));

            Outcome<Budget> changed = setBudget.execute(
                    new SetBudgetCommand(second, food, MARCH, ofUnits(600_000)));

            assertThat(changed.status()).isEqualTo(APPLIED);
            assertThat(changed.value().replaces()).contains(first);
            assertThat(liveLimitOf(food, MARCH))
                    .as("el que cuenta es el nuevo")
                    .isEqualTo(60_000_000L);
            assertThat(budgetCount())
                    .as("nada se borra: los dos siguen ahí")
                    .isEqualTo(2);
            assertThat(auditFor(second.value()))
                    .extracting("entity", "action", "before", "after")
                    .containsExactly(tuple("budget", "AMEND",
                            "{\"budget\": \"%s\"}".formatted(first), null));
        }

        @Test
        @DisplayName("U32 · una cuenta que no es de gasto no admite presupuesto, y no deja rastro")
        void onlyExpenseCategoriesAreBudgeted() {
            long auditBefore = auditCount();

            assertThatExceptionOfType(UnexpectedAccountTypeException.class).isThrownBy(
                    () -> setBudget.execute(new SetBudgetCommand(
                            BudgetId.generate(), cash, MARCH, ofUnits(500_000))));

            assertThat(budgetCount()).isZero();
            assertThat(auditCount()).isEqualTo(auditBefore);
        }

        private Long liveLimitOf(AccountId category, YearMonth month) {
            return jdbc.queryForObject(
                    "SELECT limit_cents FROM budgets WHERE category_id = ? "
                            + "AND period = ? AND superseded_at IS NULL",
                    Long.class, category.value(), month.atDay(1));
        }

        private long budgetCount() {
            return jdbc.queryForObject("SELECT count(*) FROM budgets", Long.class);
        }
    }

    private AccountId open(AccountType type, String name) {
        AccountId id = AccountId.generate();
        openAccount.execute(new OpenAccountCommand(id, type, name));
        return id;
    }

    private void startCashWith(long amount) {
        recordMovement.execute(new OpeningBalanceCommand(
                TransactionId.generate(), TODAY, cash, equity, ofUnits(amount)));
    }

    private MovementCommand lunch(TransactionId id, long amount) {
        return new ExpenseCommand(id, TODAY, "Almuerzo", cash, food, ofUnits(amount));
    }

    /** El saldo como lo lee la persona, preguntado por la capa de lectura. */
    private Money balanceOf(AccountId id) {
        return readBook.balances(TODAY).stream()
                .filter(balance -> balance.id().equals(id))
                .findFirst()
                .orElseThrow()
                .balance();
    }

    private List<Map<String, Object>> auditFor(UUID entityId) {
        return jdbc.queryForList(
                "SELECT entity, action, source::text AS source, before::text AS before, "
                        + "after::text AS after FROM audit_log WHERE entity_id = ? ORDER BY id",
                entityId);
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class);
    }

    private long transactionCount() {
        return jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class);
    }

    private String storedCancellationNote(TransactionId id) {
        return jdbc.queryForObject(
                "SELECT cancellation_note FROM transactions WHERE id = ?",
                String.class, id.value());
    }

    private String nameOf(AccountId id) {
        return jdbc.queryForObject(
                "SELECT name FROM accounts WHERE id = ?", String.class, id.value());
    }

    private Object archivedAt(AccountId id) {
        return jdbc.queryForObject(
                "SELECT archived_at FROM accounts WHERE id = ?", Object.class, id.value());
    }

    private PersonId addPerson(String name) {
        PersonId id = PersonId.generate();
        openPerson.execute(new OpenPersonCommand(id, name));
        return id;
    }

    private String nameOfPerson(PersonId id) {
        return jdbc.queryForObject(
                "SELECT name FROM people WHERE id = ?", String.class, id.value());
    }

    private Object archivedAtOfPerson(PersonId id) {
        return jdbc.queryForObject(
                "SELECT archived_at FROM people WHERE id = ?", Object.class, id.value());
    }

    private String personOf(AccountId id) {
        return jdbc.queryForObject(
                "SELECT person_id::text FROM accounts WHERE id = ?", String.class, id.value());
    }

    private long peopleCount() {
        return jdbc.queryForObject("SELECT count(*) FROM people", Long.class);
    }
}

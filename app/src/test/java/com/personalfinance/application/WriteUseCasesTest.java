package com.personalfinance.application;

import static com.personalfinance.application.Outcome.Status.ALREADY_APPLIED;
import static com.personalfinance.application.Outcome.Status.APPLIED;
import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import com.personalfinance.application.command.AmendTransactionCommand;
import com.personalfinance.application.command.ArchiveAccountCommand;
import com.personalfinance.application.command.BorrowCommand;
import com.personalfinance.application.command.CancelTransactionCommand;
import com.personalfinance.application.command.ExpenseCommand;
import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.application.command.OpenAccountCommand;
import com.personalfinance.application.command.OpeningBalanceCommand;
import com.personalfinance.application.command.ReceiveThirdPartyMoneyCommand;
import com.personalfinance.application.command.RenameAccountCommand;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.application.read.BookSummary;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.AmendTransaction;
import com.personalfinance.application.usecase.ArchiveAccount;
import com.personalfinance.application.usecase.CancelTransaction;
import com.personalfinance.application.usecase.OpenAccount;
import com.personalfinance.application.usecase.RecordMovement;
import com.personalfinance.application.usecase.RenameAccount;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountStillHoldsMoneyException;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.AmountOutOfRangeException;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.ControlCharactersException;
import com.personalfinance.domain.ledger.DuplicateAccountException;
import com.personalfinance.domain.ledger.DuplicateAccountNameException;
import com.personalfinance.domain.ledger.DuplicateTransactionException;
import com.personalfinance.domain.ledger.TextTooLongException;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionAlreadyCancelledException;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.UnknownAccountException;
import com.personalfinance.domain.shared.Money;
import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
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
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts CASCADE");

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
}

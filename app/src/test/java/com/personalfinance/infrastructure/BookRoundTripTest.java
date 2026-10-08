package com.personalfinance.infrastructure;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountAlreadyArchivedException;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.DuplicateTransactionException;
import com.personalfinance.domain.ledger.Operations;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionAlreadyCancelledException;
import com.personalfinance.domain.ledger.TransactionSource;
import com.personalfinance.domain.ledger.UnbalancedTransactionException;
import com.personalfinance.domain.ledger.port.AccountStore;
import com.personalfinance.domain.ledger.port.TransactionStore;
import com.personalfinance.infrastructure.persistence.BookLoader;
import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La prueba que importa del paso 5b: un libro completo se guarda, se vuelve a
 * cargar, y responde exactamente lo mismo.
 *
 * <p>Que cada pieza funcione por separado no prueba nada: lo que puede salir mal
 * aquí es que una cuenta se cargue como dos instancias y el saldo se parta en
 * dos, que el orden de los apuntes se pierda, o que una anulación vuelva como
 * si hubiera salido de la app cuando la hizo el bot. Todo eso se ve comparando
 * las respuestas del libro, no las filas.
 *
 * <p>{@code NOT_SUPPORTED} por lo mismo que en {@link SchemaMigrationTest}: el
 * trigger de balance corre al cerrar la transacción de base de datos, y dentro
 * de una que nunca hace commit no se dispararía nunca.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Un libro guardado")
class BookRoundTripTest extends PostgresIntegrationTest {

    private static final LocalDate JANUARY = LocalDate.of(2026, 1, 15);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final Instant WHEN = Instant.parse("2026-10-07T15:30:00Z");

    @Autowired
    private AccountStore accounts;

    @Autowired
    private TransactionStore transactions;

    @Autowired
    private BookLoader loader;

    @Autowired
    private JdbcTemplate jdbc;

    private Book written;
    private Account bancolombia;
    private Account cash;
    private Account food;
    private Account salary;
    private Account juan;
    private Account brother;
    private Account momsFund;
    private Account equity;

    @BeforeEach
    void writeABookWorthReloading() {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts CASCADE");

        written = Book.empty();
        bancolombia = open(AccountType.ASSET, "Bancolombia");
        cash = open(AccountType.ASSET, "Efectivo");
        food = open(AccountType.EXPENSE, "Alimentación");
        salary = open(AccountType.INCOME, "Salario");
        juan = open(AccountType.RECEIVABLE, "Juan");
        brother = open(AccountType.PAYABLE, "Hermano");
        momsFund = open(AccountType.THIRD_PARTY_FUND, "Mamá");
        equity = open(AccountType.EQUITY, "Saldos iniciales");

        record(Operations.openingBalance(JANUARY, bancolombia, equity, ofUnits(800_000)));
        record(Operations.income(JANUARY, "Sueldo", bancolombia, salary, ofUnits(2_000_000)));
        record(Operations.transfer(TODAY, "A efectivo", bancolombia, cash, ofUnits(200_000)));
        record(Operations.expense(TODAY, "Almuerzo", cash, food, ofUnits(35_000)));
        record(Operations.lend(TODAY, "Préstamo a Juan", bancolombia, juan, ofUnits(300_000)));
        record(Operations.borrow(TODAY, "Me prestó", brother, bancolombia, ofUnits(200_000)));
        record(Operations.receiveThirdPartyMoney(
                TODAY, "Fondo de mamá", momsFund, bancolombia, ofUnits(100_000)));
    }

    @Nested
    @DisplayName("vuelve con las mismas respuestas")
    class SameAnswers {

        @Test
        @DisplayName("R1 · los saldos de cada cuenta, uno por uno")
        void everyBalanceMatches() {
            Book loaded = loader.load();

            for (Account account : written.chart().all()) {
                Account same = loaded.chart().require(account.id());

                assertThat(loaded.ledger().displayedBalanceOf(same, TODAY))
                        .as("saldo de %s", account.name())
                        .isEqualTo(written.ledger().displayedBalanceOf(account, TODAY));
            }
        }

        @Test
        @DisplayName("R2 · el dinero que de verdad es suyo")
        void theNumberThatMatters() {
            Book loaded = loader.load();

            assertThat(loaded.ledger().moneyReallyMine(TODAY))
                    .isEqualTo(written.ledger().moneyReallyMine(TODAY));
        }

        @Test
        @DisplayName("R3 · y el libro sigue cuadrando en cero")
        void theBookStillAddsUp() {
            assertThat(loader.load().ledger().sumOfEveryEntry()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("R4 · con todos los movimientos y en el mismo orden")
        void everyMovementInOrder() {
            Book loaded = loader.load();

            assertThat(loaded.ledger().history())
                    .containsExactlyElementsOf(written.ledger().history());
        }

        @Test
        @DisplayName("R5 · y cada apunte en el orden en que se escribió")
        void entriesKeepTheirOrder() {
            Transaction lunch = written.ledger().movementsOf(food).getFirst();

            Transaction loaded = transactions.find(lunch.id(), loader.load().chart()).orElseThrow();

            assertThat(loaded.entries()).containsExactlyElementsOf(lunch.entries());
        }
    }

    @Nested
    @DisplayName("resuelve cada apunte contra una sola instancia de la cuenta")
    class OneInstancePerAccount {

        @Test
        @DisplayName("R6 · así que el saldo no se parte en dos")
        void theBalanceDoesNotSplit() {
            Book loaded = loader.load();
            Account loadedBank = loaded.chart().require(bancolombia.id());

            // Bancolombia aparece en seis movimientos distintos. Si cada apunte
            // hubiera traído su propio objeto Account, este saldo saldría en cero.
            assertThat(loaded.ledger().balanceOf(loadedBank, TODAY))
                    .isEqualTo(written.ledger().balanceOf(bancolombia, TODAY));
            assertThat(loaded.ledger().movementsOf(loadedBank)).hasSize(6);
        }
    }

    @Nested
    @DisplayName("conserva lo que pasó con sus movimientos")
    class HistoryIsKept {

        @Test
        @DisplayName("R7 · una anulación, con su motivo y de dónde salió")
        void cancellationComesBackWhole() {
            Transaction lunch = written.ledger().movementsOf(food).getFirst();
            Cancellation how = new Cancellation(WHEN, "La pagó mi hermano", TransactionSource.BOT);
            transactions.markCancelled(lunch.id(), how);

            Book loaded = loader.load();
            Transaction sameLunch =
                    transactions.find(lunch.id(), loaded.chart()).orElseThrow();

            assertThat(sameLunch.cancellation()).contains(how);
            assertThat(loaded.ledger().displayedBalanceOf(
                    loaded.chart().require(food.id()), TODAY)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("R8 · y la cadena de correcciones de un movimiento")
        void theRevisionChainComesBack() {
            Transaction lunch = written.ledger().movementsOf(food).getFirst();
            Transaction correction =
                    Operations.expense(TODAY, "Almuerzo", cash, food, ofUnits(42_000));

            Transaction replacement =
                    written.ledger().amend(lunch.id(), correction, WHEN);
            transactions.amend(replacement, lunch.cancellation().orElseThrow());

            Book loaded = loader.load();

            assertThat(loaded.ledger().revisionsOf(lunch.id()))
                    .containsExactly(lunch, replacement);
            assertThat(loaded.ledger().displayedBalanceOf(
                    loaded.chart().require(food.id()), TODAY)).isEqualTo(ofUnits(42_000));
        }

        @Test
        @DisplayName("R9 · una cuenta archivada sigue archivada, con su fecha")
        void archivedAccountsStayArchived() {
            Account oldWallet = open(AccountType.ASSET, "Daviplata");
            written.chart().archive(oldWallet.id(), written.ledger(), WHEN);
            accounts.markArchived(oldWallet.id(), WHEN);

            Account loaded = loader.load().chart().require(oldWallet.id());

            assertThat(loaded.isArchived()).isTrue();
            assertThat(loaded.archivedAt()).contains(WHEN);
        }

        @Test
        @DisplayName("R10 · y un movimiento viejo sobre una cuenta ya archivada se carga igual")
        void historyOfAnArchivedAccountLoads() {
            Account retired = open(AccountType.EXPENSE, "Suscripciones");
            record(Operations.expense(JANUARY, "Netflix", cash, retired, ofUnits(44_000)));
            written.chart().archive(retired.id(), written.ledger(), WHEN);
            accounts.markArchived(retired.id(), WHEN);

            Book loaded = loader.load();
            Account same = loaded.chart().require(retired.id());

            assertThat(same.isArchived()).isTrue();
            assertThat(loaded.ledger().displayedBalanceOf(same, TODAY)).isEqualTo(ofUnits(44_000));
        }

        @Test
        @DisplayName("R11 · y un cambio de nombre")
        void renamesComeBack() {
            written.chart().rename(cash.id(), "Billetera");
            accounts.rename(cash.id(), "Billetera");

            assertThat(loader.load().chart().require(cash.id()).name()).isEqualTo("Billetera");
        }
    }

    @Nested
    @DisplayName("se niega a cargar")
    class RefusesToLoad {

        @Test
        @DisplayName("R12 · una transacción cuyos apuntes no suman cero")
        void anUnbalancedTransaction() {
            // Insertado por SQL, saltándose el dominio: es justo el caso que el
            // paso 5b tiene que detectar en vez de propagar a todos los saldos.
            // El trigger es diferido, así que esto solo pasa por estar fuera de
            // una transacción de base de datos abierta.
            jdbc.execute("ALTER TABLE entries DISABLE TRIGGER entries_must_balance");
            try {
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO transactions (id, occurred_on, description) "
                        + "VALUES (?, ?::date, 'Corrupta')", id, TODAY.toString());
                insertEntry(id, cash.id(), -3_500_000L, 0);
                insertEntry(id, food.id(), 3_505_000L, 1);
            } finally {
                jdbc.execute("ALTER TABLE entries ENABLE TRIGGER entries_must_balance");
            }

            assertThatExceptionOfType(UnbalancedTransactionException.class)
                    .isThrownBy(() -> loader.load());
        }
    }

    @Nested
    @DisplayName("no deja que un reintento cambie lo guardado")
    class RepeatedWrites {

        /**
         * El caso de la sincronización: el teléfono envía un movimiento, se corta
         * la red antes de la respuesta, y lo vuelve a enviar. Sin
         * {@code AssignedIdRow}, el segundo envío reescribía el encabezado y
         * duplicaba los apuntes, y el saldo quedaba al doble sin un solo error.
         */
        @Test
        @DisplayName("R13 · guardar dos veces el mismo movimiento falla y el saldo no se duplica")
        void recordingTwiceFails() {
            Transaction lunch = written.ledger().movementsOf(food).getFirst();

            assertThatExceptionOfType(DuplicateTransactionException.class)
                    .isThrownBy(() -> transactions.record(lunch));

            Book loaded = loader.load();
            assertThat(loaded.ledger().displayedBalanceOf(
                    loaded.chart().require(food.id()), TODAY)).isEqualTo(ofUnits(35_000));
            assertThat(loaded.ledger().history()).hasSameSizeAs(written.ledger().history());
        }

        @Test
        @DisplayName("R14 · anular dos veces falla y queda la primera anulación")
        void cancellingTwiceKeepsTheFirst() {
            Transaction lunch = written.ledger().movementsOf(food).getFirst();
            Cancellation first = new Cancellation(WHEN, "La pagó mi hermano", TransactionSource.APP);
            transactions.markCancelled(lunch.id(), first);

            assertThatExceptionOfType(TransactionAlreadyCancelledException.class)
                    .isThrownBy(() -> transactions.markCancelled(lunch.id(),
                            new Cancellation(WHEN, "Otro motivo", TransactionSource.BOT)));

            Transaction stored = transactions.find(lunch.id(), loader.load().chart()).orElseThrow();
            assertThat(stored.cancellation()).contains(first);
        }

        @Test
        @DisplayName("R15 · abrir dos veces la misma cuenta falla y no la reescribe")
        void openingTwiceDoesNotRewrite() {
            Account impostor = Account.rehydrated(
                    cash.id(), AccountType.EXPENSE, "Otra cosa", null);

            assertThatThrownBy(() -> accounts.open(impostor))
                    .isInstanceOf(DataIntegrityViolationException.class);

            Account stored = loader.load().chart().require(cash.id());
            assertThat(stored.type()).isEqualTo(AccountType.ASSET);
            assertThat(stored.name()).isEqualTo("Efectivo");
        }

        @Test
        @DisplayName("R16 · archivar dos veces falla y conserva la fecha original")
        void archivingTwiceKeepsTheDate() {
            Account oldWallet = open(AccountType.ASSET, "Daviplata");
            accounts.markArchived(oldWallet.id(), WHEN);

            assertThatExceptionOfType(AccountAlreadyArchivedException.class)
                    .isThrownBy(() -> accounts.markArchived(
                            oldWallet.id(), WHEN.plusSeconds(3_600)));

            assertThat(loader.load().chart().require(oldWallet.id()).archivedAt())
                    .contains(WHEN);
        }
    }

    @Nested
    @DisplayName("guarda una corrección entera o no la guarda")
    class AtomicAmendment {

        /**
         * La corrección apunta a una cuenta que la base no conoce, así que su
         * segunda mitad falla. Si las dos mitades fueran commits separados, el
         * almuerzo quedaría anulado sin nada en su lugar: desaparecería de
         * todos los saldos.
         */
        @Test
        @DisplayName("R17 · si la corrección no se puede guardar, el original sigue contando")
        void aFailedAmendmentLeavesTheOriginalAlone() {
            Transaction lunch = written.ledger().movementsOf(food).getFirst();
            Account unsaved = written.chart().open(AccountType.EXPENSE, "Nunca guardada");
            Transaction replacement = written.ledger().amend(lunch.id(),
                    Operations.expense(TODAY, "Almuerzo", cash, unsaved, ofUnits(42_000)), WHEN);

            assertThatThrownBy(() ->
                    transactions.amend(replacement, lunch.cancellation().orElseThrow()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            Book loaded = loader.load();
            assertThat(transactions.find(lunch.id(), loaded.chart()).orElseThrow().isCancelled())
                    .isFalse();
            assertThat(loaded.ledger().displayedBalanceOf(
                    loaded.chart().require(food.id()), TODAY)).isEqualTo(ofUnits(35_000));
        }
    }

    // --- Ayudas.

    /** Abre la cuenta en el libro en memoria y en la base, que es lo que se compara. */
    private Account open(AccountType type, String name) {
        Account account = written.chart().open(type, name);
        accounts.open(account);
        return account;
    }

    private void record(Transaction transaction) {
        if (!written.ledger().history().contains(transaction)) {
            written.ledger().record(transaction);
        }
        transactions.record(transaction);
    }

    private void insertEntry(UUID transactionId, AccountId account, long cents, int position) {
        jdbc.update("INSERT INTO entries "
                        + "(id, transaction_id, account_id, amount_cents, position) "
                        + "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), transactionId, account.value(), cents, position);
    }
}

package com.personalfinance.infrastructure;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;



import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountAlreadyArchivedException;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.Budget;
import com.personalfinance.domain.ledger.BudgetAlreadySupersededException;
import com.personalfinance.domain.ledger.BudgetId;
import com.personalfinance.domain.ledger.DuplicateBudgetException;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.DuplicateTransactionException;
import com.personalfinance.domain.ledger.Operations;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonPosition;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionAlreadyCancelledException;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.TransactionSource;
import com.personalfinance.domain.ledger.UnbalancedTransactionException;
import com.personalfinance.domain.ledger.port.AccountStore;
import com.personalfinance.domain.ledger.port.BudgetStore;
import com.personalfinance.domain.ledger.port.PersonStore;
import com.personalfinance.domain.ledger.port.TransactionStore;
import com.personalfinance.application.BookLoader;
import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private PersonStore people;

    @Autowired
    private BudgetStore budgets;

    @Autowired
    private BookLoader loader;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate readOnly;

    private Book written;
    private Account bancolombia;
    private Account cash;
    private Account food;
    private Account salary;
    private Account juan;
    private Account brother;
    private Account momsFund;
    private Account equity;
    private Person juanHimself;
    private Person theBrother;
    private Person mother;

    @BeforeEach
    void writeABookWorthReloading() {
        readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        jdbc.execute(
                "TRUNCATE entries, transactions, audit_log, budgets, accounts, people CASCADE");

        written = Book.empty();
        bancolombia = open(AccountType.ASSET, "Bancolombia");
        cash = open(AccountType.ASSET, "Efectivo");
        food = open(AccountType.EXPENSE, "Alimentación");
        salary = open(AccountType.INCOME, "Salario");
        juan = open(AccountType.RECEIVABLE, "Juan");
        brother = open(AccountType.PAYABLE, "Hermano");
        momsFund = open(AccountType.THIRD_PARTY_FUND, "Mamá");
        equity = open(AccountType.EQUITY, "Saldos iniciales");

        juanHimself = addPerson("Juan");
        theBrother = addPerson("Hermano");
        mother = addPerson("Mamá");
        link(juan, juanHimself);
        link(brother, theBrother);
        link(momsFund, mother);

        record(Operations.openingBalance(TransactionId.generate(),
                JANUARY, bancolombia, equity, ofUnits(800_000)));
        record(Operations.income(TransactionId.generate(),
                JANUARY, "Sueldo", bancolombia, salary, ofUnits(2_000_000)));
        record(Operations.transfer(TransactionId.generate(),
                TODAY, "A efectivo", bancolombia, cash, ofUnits(200_000)));
        record(Operations.expense(TransactionId.generate(),
                TODAY, "Almuerzo", cash, food, ofUnits(35_000)));
        record(Operations.lend(TransactionId.generate(),
                TODAY, "Préstamo a Juan", bancolombia, juan, ofUnits(300_000)));
        record(Operations.borrow(TransactionId.generate(),
                TODAY, "Me prestó", brother, bancolombia, ofUnits(200_000)));
        record(Operations.receiveThirdPartyMoney(TransactionId.generate(),
                TODAY, "Fondo de mamá", momsFund, bancolombia, ofUnits(100_000)));
    }

    @Nested
    @DisplayName("vuelve con las mismas respuestas")
    class SameAnswers {

        @Test
        @DisplayName("R1 · los saldos de cada cuenta, uno por uno")
        void everyBalanceMatches() {
            Book loaded = load();

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
            Book loaded = load();

            assertThat(loaded.ledger().moneyReallyMine(TODAY))
                    .isEqualTo(written.ledger().moneyReallyMine(TODAY));
        }

        @Test
        @DisplayName("R3 · y el libro sigue cuadrando en cero")
        void theBookStillAddsUp() {
            assertThat(load().ledger().sumOfEveryEntry()).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("R4 · con todos los movimientos y en el mismo orden")
        void everyMovementInOrder() {
            Book loaded = load();

            assertThat(loaded.ledger().history())
                    .containsExactlyElementsOf(written.ledger().history());
        }

        @Test
        @DisplayName("R5 · y cada apunte en el orden en que se escribió")
        void entriesKeepTheirOrder() {
            Transaction lunch = written.ledger().movementsOf(food).getFirst();

            Transaction loaded = transactions.find(lunch.id(), load().chart()).orElseThrow();

            assertThat(loaded.entries()).containsExactlyElementsOf(lunch.entries());
        }
    }

    @Nested
    @DisplayName("resuelve cada apunte contra una sola instancia de la cuenta")
    class OneInstancePerAccount {

        @Test
        @DisplayName("R6 · así que el saldo no se parte en dos")
        void theBalanceDoesNotSplit() {
            Book loaded = load();
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

            Book loaded = load();
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
                    Operations.expense(TransactionId.generate(),
                            TODAY, "Almuerzo", cash, food, ofUnits(42_000));

            Transaction replacement =
                    written.ledger().amend(lunch.id(), correction, WHEN);
            transactions.amend(replacement, lunch.cancellation().orElseThrow());

            Book loaded = load();

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

            Account loaded = load().chart().require(oldWallet.id());

            assertThat(loaded.isArchived()).isTrue();
            assertThat(loaded.archivedAt()).contains(WHEN);
        }

        @Test
        @DisplayName("R10 · y un movimiento viejo sobre una cuenta ya archivada se carga igual")
        void historyOfAnArchivedAccountLoads() {
            Account retired = open(AccountType.EXPENSE, "Suscripciones");
            record(Operations.expense(TransactionId.generate(),
                    JANUARY, "Netflix", cash, retired, ofUnits(44_000)));
            written.chart().archive(retired.id(), written.ledger(), WHEN);
            accounts.markArchived(retired.id(), WHEN);

            Book loaded = load();
            Account same = loaded.chart().require(retired.id());

            assertThat(same.isArchived()).isTrue();
            assertThat(loaded.ledger().displayedBalanceOf(same, TODAY)).isEqualTo(ofUnits(44_000));
        }

        @Test
        @DisplayName("R11 · y un cambio de nombre")
        void renamesComeBack() {
            written.chart().rename(cash.id(), "Billetera");
            accounts.rename(cash.id(), "Billetera");

            assertThat(load().chart().require(cash.id()).name()).isEqualTo("Billetera");
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
                    .isThrownBy(() -> load());
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

            Book loaded = load();
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

            Transaction stored = transactions.find(lunch.id(), load().chart()).orElseThrow();
            assertThat(stored.cancellation()).contains(first);
        }

        @Test
        @DisplayName("R15 · abrir dos veces la misma cuenta falla y no la reescribe")
        void openingTwiceDoesNotRewrite() {
            Account impostor = Account.rehydrated(
                    cash.id(), AccountType.EXPENSE, "Otra cosa", null, null);

            assertThatThrownBy(() -> accounts.open(impostor))
                    .isInstanceOf(DataIntegrityViolationException.class);

            Account stored = load().chart().require(cash.id());
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

            assertThat(load().chart().require(oldWallet.id()).archivedAt())
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
                    Operations.expense(TransactionId.generate(),
                            TODAY, "Almuerzo", cash, unsaved, ofUnits(42_000)), WHEN);

            assertThatThrownBy(() ->
                    transactions.amend(replacement, lunch.cancellation().orElseThrow()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            Book loaded = load();
            assertThat(transactions.find(lunch.id(), loaded.chart()).orElseThrow().isCancelled())
                    .isFalse();
            assertThat(loaded.ledger().displayedBalanceOf(
                    loaded.chart().require(food.id()), TODAY)).isEqualTo(ofUnits(35_000));
        }
    }

    /**
     * Las personas, que son el incremento 2. Lo que puede salir mal aquí es
     * peor que un saldo equivocado: una cuenta que vuelve sin su persona deja
     * una deuda viva fuera de la posición de alguien, con todos los saldos
     * correctos y nadie quejándose.
     */
    @Nested
    @DisplayName("recuerda de quién es cada cuenta")
    class PeopleComeBack {

        @Test
        @DisplayName("R18 · y la resuelve contra una sola instancia por persona")
        void onePersonInstance() {
            Book loaded = load();

            Person loadedJuan = loaded.directory().require(juanHimself.id());
            Account loadedAccount = loaded.chart().require(juan.id());

            assertThat(loadedAccount.person()).containsSame(loadedJuan);
            assertThat(loaded.chart().allOf(loadedJuan)).containsExactly(loadedAccount);
        }

        @Test
        @DisplayName("R19 · así que la posición con cada persona da lo mismo que antes")
        void everyPositionMatches() {
            Book loaded = load();

            for (Person person : written.directory().all()) {
                PersonPosition before =
                        PersonPosition.of(person, written.chart(), written.ledger(), TODAY);
                PersonPosition after = PersonPosition.of(
                        loaded.directory().require(person.id()),
                        loaded.chart(), loaded.ledger(), TODAY);

                assertThat(after).as("posición con %s", person.name()).isEqualTo(before);
            }
        }

        @Test
        @DisplayName("R20 · una cuenta que no es de nadie vuelve sin persona")
        void anAccountWithoutAPerson() {
            assertThat(load().chart().require(bancolombia.id()).person()).isEmpty();
        }

        /**
         * Un vecino al que nunca se le movió nada, que es la única forma de
         * archivarlo: su cuenta tiene que estar archivada antes, y una cuenta
         * que guarda plata no se archiva con saldo.
         */
        @Test
        @DisplayName("R21 · una persona archivada vuelve archivada, y su cuenta con ella")
        void anArchivedPersonComesBackArchived() {
            Person vecino = addPerson("Vecino");
            Account owedToVecino = open(AccountType.PAYABLE, "Vecino");
            link(owedToVecino, vecino);

            written.chart().archive(owedToVecino.id(), written.ledger(), WHEN);
            accounts.markArchived(owedToVecino.id(), WHEN);
            written.directory().archive(vecino.id(), written.chart(), WHEN);
            people.markArchived(vecino.id(), WHEN);

            Book loaded = load();
            Person loadedVecino = loaded.directory().require(vecino.id());

            assertThat(loadedVecino.archivedAt()).contains(WHEN);
            assertThat(loaded.chart().require(owedToVecino.id()).person())
                    .containsSame(loadedVecino);
        }

        @Test
        @DisplayName("R22 · mover el vínculo a otra persona queda guardado")
        void movingTheLinkIsStored() {
            link(juan, theBrother);

            Book loaded = load();
            Person loadedBrother = loaded.directory().require(theBrother.id());

            assertThat(loaded.chart().require(juan.id()).person()).containsSame(loadedBrother);
            assertThat(loaded.chart().allOf(
                    loaded.directory().require(juanHimself.id()))).isEmpty();
        }

        @Test
        @DisplayName("R23 · y un nombre cambiado vuelve cambiado")
        void renamingIsStored() {
            people.rename(juanHimself.id(), "Juan Pérez");

            assertThat(load().directory().require(juanHimself.id()).name())
                    .isEqualTo("Juan Pérez");
        }

        /**
         * Por lo mismo que abrir dos veces una cuenta: sin la clave primaria
         * poniéndose de por medio, "escribir" una persona que ya existía podría
         * cambiarle el nombre o desarchivarla sin dejar rastro.
         */
        @Test
        @DisplayName("R24 · escribir dos veces la misma persona falla y no la reescribe")
        void writingTwiceDoesNotRewrite() {
            Person impostor = Person.rehydrated(juanHimself.id(), "Otra persona", null);

            assertThatThrownBy(() -> people.open(impostor))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(load().directory().require(juanHimself.id()).name()).isEqualTo("Juan");
        }
    }

    // --- Ayudas.

    /**
     * El libro, cargado dentro de una transacción de solo lectura.
     *
     * <p>{@link BookLoader} es {@code MANDATORY}: solo se carga dentro de la
     * transacción de quien lo pide, que es la que fija el aislamiento. Esta
     * clase corre con {@code NOT_SUPPORTED}, así que aquí hay que abrirla a
     * mano.
     */
    private Book load() {
        return readOnly.execute(status -> loader.load());
    }

    /**
     * R25 a R29. Los presupuestos: que vuelvan con su categoría, su mes y su
     * límite, y que el reemplazo vuelva encadenado.
     */
    @Nested
    @DisplayName("los presupuestos")
    class TheBudgets {

        private static final YearMonth MARCH = YearMonth.of(2026, 3);

        @Test
        @DisplayName("R25 · un límite vuelve con su categoría, su mes y su monto")
        void aBudgetComesBackWhole() {
            Budget limit = setBudget(food, MARCH, 500_000);

            Budget loaded = load().budgets().find(limit.id()).orElseThrow();

            assertThat(loaded.month()).isEqualTo(MARCH);
            assertThat(loaded.limit()).isEqualTo(ofUnits(500_000));
            assertThat(loaded.isLive()).isTrue();
            assertThat(loaded.replaces()).isEmpty();
        }

        /**
         * La categoría tiene que ser <em>la</em> del catálogo cargado y no una
         * copia, por lo mismo que los apuntes y que la persona de una cuenta:
         * dos objetos llamados «Alimentación» son dos categorías, y el gasto de
         * una se mediría contra el límite de la otra.
         */
        @Test
        @DisplayName("R26 · y su categoría es la del catálogo, no una copia")
        void theCategoryIsTheOneFromTheChart() {
            Budget limit = setBudget(food, MARCH, 500_000);

            Book loaded = load();

            assertThat(loaded.budgets().find(limit.id()).orElseThrow().category())
                    .isSameAs(loaded.chart().require(food.id()));
        }

        @Test
        @DisplayName("R27 · un reemplazo vuelve encadenado, y el anterior marcado")
        void aReplacementComesBackChained() {
            Budget first = setBudget(food, MARCH, 500_000);
            Budget second = replaceBudget(food, MARCH, 600_000);

            Book loaded = load();
            Budget loadedFirst = loaded.budgets().find(first.id()).orElseThrow();
            Budget loadedSecond = loaded.budgets().find(second.id()).orElseThrow();

            assertThat(loadedSecond.replaces()).contains(first.id());
            assertThat(loadedFirst.isLive()).isFalse();
            assertThat(loadedFirst.supersededAt()).contains(WHEN);
            assertThat(loaded.budgets().liveFor(loaded.chart().require(food.id()), MARCH))
                    .contains(loadedSecond);
        }

        @Test
        @DisplayName("R28 · escribir dos veces el mismo límite falla y no lo reescribe")
        void writingTwiceDoesNotRewrite() {
            Budget limit = setBudget(food, MARCH, 500_000);
            Budget impostor = Budget.rehydrated(
                    limit.id(), food, MARCH, ofUnits(900_000), null, null);

            assertThatThrownBy(() -> budgets.set(impostor))
                    .isInstanceOf(DuplicateBudgetException.class);

            assertThat(load().budgets().find(limit.id()).orElseThrow().limit())
                    .isEqualTo(ofUnits(500_000));
        }

        @Test
        @DisplayName("R29 · y reemplazar dos veces el mismo falla")
        void supersedingTwiceFails() {
            setBudget(food, MARCH, 500_000);
            replaceBudget(food, MARCH, 600_000);

            Budget another = Budget.rehydrated(BudgetId.generate(), food, MARCH,
                    ofUnits(700_000), written.budgets().all().get(0).id(), null);

            assertThatThrownBy(() -> budgets.replace(another, WHEN))
                    .isInstanceOf(BudgetAlreadySupersededException.class);
        }

        private Budget setBudget(Account category, YearMonth month, long units) {
            Budget limit = written.budgets().set(
                    BudgetId.generate(), category, month, ofUnits(units), WHEN);
            budgets.set(limit);
            return limit;
        }

        private Budget replaceBudget(Account category, YearMonth month, long units) {
            Budget limit = written.budgets().set(
                    BudgetId.generate(), category, month, ofUnits(units), WHEN);
            budgets.replace(limit, WHEN);
            return limit;
        }
    }

    /** Abre la cuenta en el libro en memoria y en la base, que es lo que se compara. */
    private Account open(AccountType type, String name) {
        Account account = written.chart().open(type, name);
        accounts.open(account);
        return account;
    }

    private Person addPerson(String name) {
        Person person = written.directory().open(name);
        people.open(person);
        return person;
    }

    private void link(Account account, Person person) {
        written.chart().linkToPerson(account.id(), person);
        accounts.linkToPerson(account.id(), person.id());
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

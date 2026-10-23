package com.personalfinance.application;

import static com.personalfinance.application.Outcome.Status.ALREADY_APPLIED;
import static com.personalfinance.application.Outcome.Status.APPLIED;
import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.application.command.ArchiveAccountCommand;
import com.personalfinance.application.command.ArchivePersonCommand;
import com.personalfinance.application.command.ExpenseCommand;
import com.personalfinance.application.command.LinkAccountToPersonCommand;
import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.application.command.OpenAccountCommand;
import com.personalfinance.application.command.OpenPersonCommand;
import com.personalfinance.application.command.OpeningBalanceCommand;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.ArchiveAccount;
import com.personalfinance.application.usecase.ArchivePerson;
import com.personalfinance.application.usecase.LinkAccountToPerson;
import com.personalfinance.application.usecase.OpenAccount;
import com.personalfinance.application.usecase.OpenPerson;
import com.personalfinance.application.usecase.RecordMovement;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.LedgerException;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que pasa cuando dos cosas llegan a la vez.
 *
 * <p>Que la aplicación sea de un solo usuario no quita el problema: el cliente
 * reintenta por un timeout mientras la primera petición todavía corre, y hay
 * reglas que cruzan filas y que la base no ve por su cuenta. Estos tests son la
 * única forma de comprobar que el turno de escritura hace su trabajo.
 *
 * <p>No pueden ir dentro de una transacción de test: cada hilo abre la suya y
 * lo que se mide es cómo se ordenan entre ellas. Los hilos que se lanzan son
 * menos que las conexiones del pool de Hikari (diez por omisión), porque si el
 * pool fuera el cuello de botella el test mediría el pool y no el candado.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Dos escrituras a la vez")
class ConcurrencyTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final int WRITERS = 6;
    private static final int ROUNDS = 20;
    private static final Duration READING_FOR = Duration.ofSeconds(2);

    @Autowired
    private RecordMovement recordMovement;

    @Autowired
    private ArchiveAccount archiveAccount;

    @Autowired
    private OpenAccount openAccount;

    @Autowired
    private OpenPerson openPerson;

    @Autowired
    private ArchivePerson archivePerson;

    @Autowired
    private LinkAccountToPerson linkAccountToPerson;

    @Autowired
    private ReadBook readBook;

    @Autowired
    private JdbcTemplate jdbc;

    private AccountId cash;
    private AccountId food;
    private AccountId equity;

    @BeforeEach
    void startFromAnEmptyBook() {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts, people CASCADE");

        cash = open(AccountType.ASSET, "Efectivo");
        food = open(AccountType.EXPENSE, "Alimentación");
        equity = open(AccountType.EQUITY, "Saldos iniciales");
    }

    @Test
    @DisplayName("C1 · seis movimientos distintos a la vez quedan los seis, y el libro cuadra")
    void everyConcurrentWriteLands() throws Exception {
        startCashWith(100_000);

        List<TransactionId> ids = IntStream.range(0, WRITERS)
                .mapToObj(each -> TransactionId.generate())
                .toList();

        runTogether(ids.stream()
                .map(id -> (Callable<Outcome.Status>) () ->
                        recordMovement.execute(lunch(id, 1_000)).status())
                .toList());

        assertThat(transactionCount()).isEqualTo(WRITERS + 1);
        assertThat(sumOfEveryEntry()).isZero();
        assertThat(balanceOf(cash)).isEqualTo(ofUnits(94_000));
        assertThat(entryCount()).isEqualTo((WRITERS + 1) * 2);
    }

    /**
     * El caso que justifica el candado. Archivar exige saldo eventual cero, y
     * esa pregunta cruza todo el libro: sin turno, la cuenta termina archivada
     * con plata adentro, y archivar no se deshace.
     */
    @Test
    @DisplayName("C2 · archivar y gastar a la vez: gana exactamente uno, veinte veces")
    void archivingRacesAgainstSpending() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            AccountId wallet = open(AccountType.ASSET, "Caja " + round);

            List<Boolean> won = runTogether(List.of(
                    attempt(() -> archiveAccount.execute(new ArchiveAccountCommand(wallet))),
                    attempt(() -> recordMovement.execute(new ExpenseCommand(
                            TransactionId.generate(), TODAY, "Gasto", wallet, food,
                            ofUnits(10_000))))));

            assertThat(won)
                    .as("ronda %d: uno de los dos tenía que perder", round)
                    .containsExactlyInAnyOrder(true, false);
            assertThat(archivedAccountsHoldingMoney())
                    .as("ronda %d: ninguna cuenta archivada puede tener saldo", round)
                    .isZero();
        }
    }

    @Test
    @DisplayName("C3 · el mismo movimiento desde dos hilos se escribe una sola vez")
    void theSameCommandFromTwoThreads() throws Exception {
        startCashWith(100_000);
        MovementCommand lunch = lunch(TransactionId.generate(), 35_000);

        List<Outcome.Status> results = runTogether(List.<Callable<Outcome.Status>>of(
                () -> recordMovement.execute(lunch).status(),
                () -> recordMovement.execute(lunch).status()));

        assertThat(results).containsExactlyInAnyOrder(APPLIED, ALREADY_APPLIED);
        assertThat(transactionCount()).isEqualTo(2);
        assertThat(balanceOf(cash)).isEqualTo(ofUnits(65_000));
        assertThat(auditCount()).isEqualTo(5);
    }

    /**
     * Las lecturas no toman el candado, así que corren mientras se escribe. Lo
     * que las protege es el aislamiento: {@code BookLoader} hace dos consultas
     * y en READ COMMITTED una cuenta abierta entre las dos deja un apunte
     * apuntando a una cuenta que la lectura no cargó.
     */
    @Test
    @DisplayName("C5 · leer mientras se escribe no falla ni una vez")
    void readingUnderWrites() throws Exception {
        startCashWith(1_000_000);

        CountDownLatch stop = new CountDownLatch(1);
        List<Integer> counts = runTogether(List.<Callable<Integer>>of(
                () -> writeUntil(stop),
                () -> readUntil(stop)));

        assertThat(counts).allSatisfy(done ->
                assertThat(done).as("algo tiene que haber pasado en los dos hilos")
                        .isPositive());
    }

    /**
     * El mismo caso que C2, un nivel más arriba. Archivar a una persona exige
     * que cada cuenta suya esté archivada, y esa pregunta cruza filas: sin
     * turno, Juan termina archivado con una cuenta viva colgando, y archivar no
     * se deshace.
     *
     * <p>El vínculo en carrera es el que se mueve a Juan, no el que se le
     * quita, porque quitarlo no existe. La cuenta vinculada está abierta, así
     * que si el vínculo gana, el archivado tiene que perder.
     */
    @Test
    @DisplayName("C8 · archivar una persona y vincularle una cuenta: gana uno, veinte veces")
    void archivingAPersonRacesAgainstLinking() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            PersonId juan = addPerson("Juan " + round);
            AccountId owed = open(AccountType.RECEIVABLE, "Deuda " + round);

            List<Boolean> won = runTogether(List.of(
                    attempt(() -> archivePerson.execute(new ArchivePersonCommand(juan))),
                    attempt(() -> linkAccountToPerson.execute(
                            new LinkAccountToPersonCommand(owed, juan)))));

            assertThat(won)
                    .as("ronda %d: uno de los dos tenía que perder", round)
                    .containsExactlyInAnyOrder(true, false);
            assertThat(archivedPeopleWithOpenAccounts())
                    .as("ronda %d: nadie archivado puede tener cuentas abiertas", round)
                    .isZero();
        }
    }

    private int writeUntil(CountDownLatch stop) {
        long deadline = System.nanoTime() + READING_FOR.toNanos();
        int written = 0;
        try {
            while (System.nanoTime() < deadline) {
                AccountId category = open(AccountType.EXPENSE, "Categoría " + written);
                recordMovement.execute(new ExpenseCommand(
                        TransactionId.generate(), TODAY, "Gasto", cash, category,
                        ofUnits(1_000)));
                written++;
            }
        } finally {
            stop.countDown();
        }
        return written;
    }

    private int readUntil(CountDownLatch stop) throws InterruptedException {
        int read = 0;
        while (!stop.await(1, TimeUnit.MILLISECONDS)) {
            readBook.summary(TODAY);
            readBook.balances(TODAY);
            read++;
        }
        return read;
    }

    // --- Ayudas.

    /** Un intento que solo informa si pudo: la regla que lo rechazó ya está probada. */
    private static Callable<Boolean> attempt(Runnable write) {
        return () -> {
            try {
                write.run();
                return true;
            } catch (LedgerException refused) {
                return false;
            }
        };
    }

    /**
     * Lanza todas las tareas y las suelta al mismo tiempo. Sin el disparo
     * común, la primera suele terminar antes de que la última arranque y no
     * habría concurrencia que medir.
     */
    private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch gun = new CountDownLatch(1);
        try {
            List<Future<T>> running = tasks.stream()
                    .map(task -> pool.submit(() -> {
                        gun.await();
                        return task.call();
                    }))
                    .toList();

            gun.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : running) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
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

    private Money balanceOf(AccountId id) {
        return readBook.balances(TODAY).stream()
                .filter(balance -> balance.id().equals(id))
                .findFirst()
                .orElseThrow()
                .balance();
    }

    private PersonId addPerson(String name) {
        PersonId id = PersonId.generate();
        openPerson.execute(new OpenPersonCommand(id, name));
        return id;
    }

    private long archivedPeopleWithOpenAccounts() {
        return jdbc.queryForObject("""
                SELECT count(*)
                  FROM people p
                  JOIN accounts a ON a.person_id = p.id
                 WHERE p.archived_at IS NOT NULL
                   AND a.archived_at IS NULL
                """, Long.class);
    }

    private long archivedAccountsHoldingMoney() {
        return jdbc.queryForObject("""
                SELECT count(*)
                  FROM account_eventual_balances b
                  JOIN accounts a ON a.id = b.account_id
                 WHERE a.archived_at IS NOT NULL
                   AND b.ledger_cents <> 0
                """, Long.class);
    }

    private long transactionCount() {
        return jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class);
    }

    private long entryCount() {
        return jdbc.queryForObject("SELECT count(*) FROM entries", Long.class);
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class);
    }

    private long sumOfEveryEntry() {
        return jdbc.queryForObject("SELECT coalesce(sum(amount_cents), 0) FROM entries",
                Long.class);
    }
}

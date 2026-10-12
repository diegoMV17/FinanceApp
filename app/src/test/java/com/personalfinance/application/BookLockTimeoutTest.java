package com.personalfinance.application;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.personalfinance.application.command.ExpenseCommand;
import com.personalfinance.application.command.OpenAccountCommand;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.application.usecase.OpenAccount;
import com.personalfinance.application.usecase.RecordMovement;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * C4. Qué pasa cuando el turno no llega.
 *
 * <p>Esto comprueba algo que no se puede dar por sabido: que
 * {@code lock_timeout} de Postgres también corta la espera de un
 * {@code pg_advisory_xact_lock}, y no solo la de los candados de tabla y fila.
 * Si no aplicara, una escritura esperaría para siempre detrás de otra colgada y
 * habría que cambiar a {@code pg_try_advisory_xact_lock} con su propio plazo.
 *
 * <p>Un segundo en vez de los cinco de producción: la propiedad se sobreescribe
 * aquí, lo que le da a esta clase su propio contexto y su propio contenedor. Es
 * el precio de probar la espera sin que el test tarde cinco segundos.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "finance.book-lock.timeout=1s")
@DisplayName("Cuando el libro está ocupado")
class BookLockTimeoutTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Autowired
    private RecordMovement recordMovement;

    @Autowired
    private OpenAccount openAccount;

    @Autowired
    private BookLock lock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private AccountId cash;
    private AccountId food;

    @BeforeEach
    void startFromAnEmptyBook() {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts CASCADE");

        cash = open(AccountType.ASSET, "Efectivo");
        food = open(AccountType.EXPENSE, "Alimentación");
    }

    @Test
    @DisplayName("C4 · la escritura se rinde al vencerse la espera, y reintentarla funciona")
    void aWriteGivesUpWhenTheTurnNeverComes() throws Exception {
        ExecutorService holder = Executors.newSingleThreadExecutor();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        long auditBefore = auditCount();
        ExpenseCommand lunch = new ExpenseCommand(
                TransactionId.generate(), TODAY, "Almuerzo", cash, food, ofUnits(35_000));

        // Otra transacción se queda con el turno más tiempo que la espera. Toma
        // el candado por el mismo puerto que la aplicación, así que no hace
        // falta conocer su clave para bloquearlo.
        Future<?> keepingItBusy = holder.submit(() -> inTransaction(() -> {
            lock.acquire();
            holding.countDown();
            awaitQuietly(release);
        }));

        try {
            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

            long startedAt = System.nanoTime();
            assertThatExceptionOfType(BookBusyException.class)
                    .isThrownBy(() -> recordMovement.execute(lunch));
            Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(waited)
                    .as("se rinde cerca del plazo, no antes ni mucho después")
                    .isGreaterThanOrEqualTo(TIMEOUT.minusMillis(100))
                    .isLessThan(TIMEOUT.multipliedBy(4));

            // Nada escrito: ni el movimiento ni una línea de bitácora.
            assertThat(transactionCount()).isZero();
            assertThat(auditCount()).isEqualTo(auditBefore);
        } finally {
            release.countDown();
            keepingItBusy.get(10, TimeUnit.SECONDS);
            holder.shutdownNow();
        }

        // Y con el turno libre, el mismo comando pasa sin más: eso es lo que
        // hace que un 503 con Retry-After sea una respuesta honesta.
        assertThat(recordMovement.execute(lunch).wasApplied()).isTrue();
        assertThat(transactionCount()).isEqualTo(1);
    }

    // --- Ayudas.

    private AccountId open(AccountType type, String name) {
        AccountId id = AccountId.generate();
        openAccount.execute(new OpenAccountCommand(id, type, name));
        return id;
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("nadie soltó el candado");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private long transactionCount() {
        return jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class);
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class);
    }
}

package com.personalfinance.application;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doAnswer;

import com.personalfinance.application.command.ExpenseCommand;
import com.personalfinance.application.command.OpenAccountCommand;
import com.personalfinance.application.command.OpeningBalanceCommand;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.OpenAccount;
import com.personalfinance.application.usecase.RecordMovement;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.port.AccountStore;
import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * C6. Por qué las lecturas van en REPEATABLE READ.
 *
 * <p>{@code BookLoader} hace dos consultas: primero el catálogo, después los
 * movimientos. En READ COMMITTED cada sentencia ve una foto nueva, así que una
 * escritura que se cuele entre las dos deja un apunte apuntando a una cuenta
 * que la primera consulta no trajo, y la carga revienta con
 * {@code UnknownAccountException}: una lectura que falla sin que nada esté mal.
 *
 * <p>C5 no alcanza a demostrarlo. La ventana entre las dos consultas dura
 * microsegundos y acertarle por casualidad es improbable; pasa en verde incluso
 * con el aislamiento equivocado. Aquí la ventana se abre a propósito —
 * espiando al puerto de cuentas para escribir justo después de la primera
 * consulta — y así el test deja de depender de la suerte.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Una lectura con una escritura en medio")
class ReadIsolationTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @MockitoSpyBean
    private AccountStore accounts;

    @Autowired
    private RecordMovement recordMovement;

    @Autowired
    private OpenAccount openAccount;

    @Autowired
    private ReadBook readBook;

    @Autowired
    private JdbcTemplate jdbc;

    private AccountId cash;
    private AccountId equity;

    @BeforeEach
    void startFromAnEmptyBook() {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts CASCADE");

        cash = open(AccountType.ASSET, "Efectivo");
        equity = open(AccountType.EQUITY, "Saldos iniciales");

        recordMovement.execute(new OpeningBalanceCommand(
                TransactionId.generate(), TODAY, cash, equity, ofUnits(1_000_000)));
    }

    @Test
    @DisplayName("C6 · no la ve a medias: ni falla ni mezcla dos fotos del libro")
    void aWriteBetweenTheTwoQueriesDoesNotBreakTheRead() {
        interfereOnceAfterLoadingTheAccounts();

        assertThatNoException().isThrownBy(() -> readBook.summary(TODAY));
        assertThatNoException().isThrownBy(() -> readBook.balances(TODAY));

        // Y lo que se coló sí está, en la lectura siguiente.
        assertThat(readBook.balances(TODAY)).hasSize(3);
    }

    /**
     * Deja que la consulta de cuentas devuelva lo suyo y, antes de que la de
     * movimientos corra, otro hilo abre una cuenta y registra un movimiento en
     * ella, los dos ya con commit. El hilo aparte es obligatorio: la escritura
     * tiene que ser otra transacción, no esta.
     */
    private void interfereOnceAfterLoadingTheAccounts() {
        AtomicBoolean pending = new AtomicBoolean(true);

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Account> loaded = (List<Account>) invocation.callRealMethod();

            // Una sola vez, y nunca dentro de la escritura que ella misma
            // provoca: esa también pasa por aquí.
            if (pending.compareAndSet(true, false)) {
                inAnotherThread(this::openACategoryAndSpendOnIt);
            }
            return loaded;
        }).when(accounts).all();
    }

    private void openACategoryAndSpendOnIt() {
        AccountId category = open(AccountType.EXPENSE, "Colada");
        recordMovement.execute(new ExpenseCommand(
                TransactionId.generate(), TODAY, "Colada", cash, category, ofUnits(12_000)));
    }

    private AccountId open(AccountType type, String name) {
        AccountId id = AccountId.generate();
        openAccount.execute(new OpenAccountCommand(id, type, name));
        return id;
    }

    private static void inAnotherThread(Runnable work) {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            other.submit(work).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (ExecutionException | java.util.concurrent.TimeoutException failed) {
            throw new IllegalStateException(failed);
        } finally {
            other.shutdownNow();
        }
    }
}

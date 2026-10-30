package com.personalfinance.application;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.personalfinance.application.command.AmendTransactionCommand;
import com.personalfinance.application.command.ExpenseCommand;
import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.application.command.OpenAccountCommand;
import com.personalfinance.application.command.OpeningBalanceCommand;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.AmendTransaction;
import com.personalfinance.application.usecase.OpenAccount;
import com.personalfinance.application.usecase.RecordMovement;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import com.personalfinance.domain.ledger.port.TransactionStore;
import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * U6. Una corrección se guarda entera o no se guarda.
 *
 * <p>Es la misma idea que R17, un nivel más arriba: ahí fallaba la segunda
 * mitad por una clave ajena, aquí se la hace fallar a propósito después de que
 * la primera ya escribió en la base. Si las dos mitades no estuvieran en la
 * misma transacción, el almuerzo quedaría anulado y sin reemplazo: desaparecido
 * de todos los saldos, que es la peor forma de perder un dato.
 *
 * <p>Se necesita un espía porque desde el caso de uso no hay entrada legítima
 * que haga fallar la segunda mitad — todas las cuentas que trae el catálogo
 * existen en la base, y los límites de Java y los del esquema coinciden. Eso es
 * buena señal, pero deja este camino sin probar, y es el camino que importa.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Una corrección a medio guardar")
class AmendAtomicityTest extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @MockitoSpyBean
    private TransactionStore transactions;

    @Autowired
    private RecordMovement recordMovement;

    @Autowired
    private AmendTransaction amendTransaction;

    @Autowired
    private OpenAccount openAccount;

    @Autowired
    private ReadBook readBook;

    @Autowired
    private JdbcTemplate jdbc;

    private AccountId cash;
    private AccountId food;
    private AccountId equity;

    @BeforeEach
    void startFromAnEmptyBook() {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts CASCADE");

        cash = open(AccountType.ASSET, "Efectivo");
        food = open(AccountType.EXPENSE, "Alimentación");
        equity = open(AccountType.EQUITY, "Saldos iniciales");

        recordMovement.execute(new OpeningBalanceCommand(
                TransactionId.generate(), TODAY, cash, equity, ofUnits(100_000)));
    }

    @Test
    @DisplayName("U6 · deja la original contando, y no deja ni rastro de la corrección")
    void aFailedAmendmentLeavesTheOriginalCounting() {
        TransactionId original = TransactionId.generate();
        recordMovement.execute(lunch(original, 35_000));
        MovementCommand correction = lunch(TransactionId.generate(), 40_000);

        // La primera mitad de verdad contra la base, y la segunda revienta.
        doAnswer(invocation -> {
            Transaction replacement = invocation.getArgument(0);
            Cancellation ofOriginal = invocation.getArgument(1);
            transactions.markCancelled(replacement.replaces().orElseThrow(), ofOriginal);
            throw new DataIntegrityViolationException("la corrección no se pudo guardar");
        }).when(transactions).amend(any(), any());

        assertThatThrownBy(() -> amendTransaction.execute(
                new AmendTransactionCommand(original, correction)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(cancelledAt(original)).isNull();
        assertThat(exists(correction.id())).isFalse();
        assertThat(balanceOf(food)).isEqualTo(ofUnits(35_000));
        assertThat(auditActionsFor(original)).containsExactly("CREATE");
    }

    // --- Ayudas.

    private AccountId open(AccountType type, String name) {
        AccountId id = AccountId.generate();
        openAccount.execute(new OpenAccountCommand(id, type, name));
        return id;
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

    private Object cancelledAt(TransactionId id) {
        return jdbc.queryForObject(
                "SELECT cancelled_at FROM transactions WHERE id = ?", Object.class, id.value());
    }

    private boolean exists(TransactionId id) {
        return jdbc.queryForObject(
                "SELECT exists(SELECT 1 FROM transactions WHERE id = ?)",
                Boolean.class, id.value());
    }

    private List<String> auditActionsFor(TransactionId id) {
        return jdbc.queryForList(
                "SELECT action FROM audit_log WHERE entity_id = ? ORDER BY id",
                String.class, id.value());
    }
}

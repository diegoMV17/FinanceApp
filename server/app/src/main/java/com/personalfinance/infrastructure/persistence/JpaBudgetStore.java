package com.personalfinance.infrastructure.persistence;

import com.personalfinance.domain.ledger.Budget;
import com.personalfinance.domain.ledger.BudgetAlreadySupersededException;
import com.personalfinance.domain.ledger.BudgetId;
import com.personalfinance.domain.ledger.Chart;
import com.personalfinance.domain.ledger.DuplicateBudgetException;
import com.personalfinance.domain.ledger.UnknownBudgetException;
import com.personalfinance.domain.ledger.port.BudgetStore;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los presupuestos, contra Postgres.
 *
 * <p>No hay {@code delete} y no hay forma de cambiar un límite: un presupuesto
 * se reemplaza, nunca se edita. Lo único que cambia en una fila ya escrita es
 * {@code superseded_at}, y los triggers de la V7 son la red de atrás.
 *
 * <p>Esta clase no da por hecho que el libro en memoria ya comprobó nada:
 * escribir dos veces el mismo id falla y reemplazar dos veces el mismo
 * presupuesto falla, las dos con la excepción del dominio. Es la misma decisión
 * que en los otros stores, y existe porque el día que alguien escriba otro
 * camino de escritura — el bot, una importación — el que se va a equivocar es
 * ese camino, no este.
 */
@Repository
class JpaBudgetStore implements BudgetStore {

    private final BudgetRowRepository budgets;

    JpaBudgetStore(BudgetRowRepository budgets) {
        this.budgets = budgets;
    }

    @Override
    @Transactional
    public void set(Budget budget) {
        if (budgets.existsById(budget.id().value())) {
            throw new DuplicateBudgetException(budget.id());
        }
        budgets.saveAndFlush(LedgerMapper.rowOf(budget));
    }

    /**
     * Las dos mitades de un reemplazo dentro del mismo {@code @Transactional}:
     * si la segunda falla, la primera se deshace con ella. Con solo la primera
     * quedarían dos límites vivos para el mismo mes — y el índice único parcial
     * de la V7 lo rechazaría —; con solo la segunda, el mes se quedaría sin
     * ninguno.
     *
     * <p>Las llamadas internas no pasan por el proxy de Spring, así que no
     * abren transacciones propias: corren dentro de esta.
     */
    @Override
    @Transactional
    public void replace(Budget replacement, Instant when) {
        BudgetId previous = replacement.replaces().orElseThrow(() ->
                new IllegalArgumentException(
                        "Budget %s does not replace any other".formatted(replacement.id())));

        supersede(previous, when);
        set(replacement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Budget> all(Chart chart) {
        return budgets.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(row -> LedgerMapper.budgetFrom(row, chart))
                .toList();
    }

    private void supersede(BudgetId id, Instant when) {
        BudgetRow row = budgets.findById(id.value())
                .orElseThrow(() -> new UnknownBudgetException(id));

        if (row.isSuperseded()) {
            throw new BudgetAlreadySupersededException(id, row.supersededAt());
        }
        row.supersede(when);
    }
}

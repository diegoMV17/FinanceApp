package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.SetBudgetCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.Budget;
import com.personalfinance.domain.ledger.BudgetId;
import com.personalfinance.domain.ledger.DuplicateBudgetException;
import com.personalfinance.domain.ledger.port.BudgetStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU26. Poner el límite de gasto de una categoría para un mes.
 *
 * <p>Un solo caso de uso para lo que el dueño vive como una sola acción. Si no
 * había límite, lo registra; si había otro, registra el nuevo y deja el
 * anterior marcado con el momento en que dejó de contar. Quién de las dos cosas
 * pasó lo decide el dominio, y aquí solo se traduce a cómo se guarda y a cómo
 * se anota.
 *
 * <p><strong>La idempotencia tiene dos caras</strong>, y las dos hacen falta.
 * La del id es la de siempre: el mismo id con el mismo contenido es un
 * reintento y el mismo id con otro contenido es una contradicción. La otra es
 * la del hecho, como en {@link LinkAccountToPerson}: si el límite vivo de esa
 * categoría y ese mes ya dice justo ese monto, mandarlo otra vez con un id
 * nuevo no es un cambio de idea, es la misma frase repetida. Sin eso, un
 * cliente que reintenta generando un id nuevo iría encadenando límites
 * idénticos, cada uno reemplazando al anterior, y la historia del mes se
 * llenaría de ruido que no pasó.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class SetBudget {

    private final BookLock lock;
    private final BookLoader loader;
    private final BudgetStore budgets;
    private final AuditTrail audit;
    private final Clock clock;

    SetBudget(
            BookLock lock,
            BookLoader loader,
            BudgetStore budgets,
            AuditTrail audit,
            Clock clock) {

        this.lock = lock;
        this.loader = loader;
        this.budgets = budgets;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * @return el límite que quedó vivo para esa categoría y ese mes
     * @throws com.personalfinance.domain.ledger.UnknownAccountException
     *         si el catálogo no tiene esa cuenta
     * @throws com.personalfinance.domain.ledger.UnexpectedAccountTypeException
     *         si no es una categoría de gasto
     * @throws com.personalfinance.domain.ledger.ArchivedAccountException
     *         si la categoría está archivada
     * @throws DuplicateBudgetException
     *         si ese id ya está usado con otro contenido
     */
    @Transactional
    public Outcome<Budget> execute(SetBudgetCommand command) {
        lock.acquire();
        Book book = loader.load();

        Account category = book.chart().require(command.category());

        Optional<Budget> underThatId = book.budgets().find(command.id());
        if (underThatId.isPresent()) {
            return sameOrConflict(underThatId.get(), command, category);
        }

        Optional<Budget> live = book.budgets().liveFor(category, command.month());
        if (live.isPresent() && live.get().hasLimit(command.limit())) {
            return Outcome.alreadyApplied(live.get());
        }

        Instant now = clock.instant();
        Budget current = book.budgets().set(
                command.id(), category, command.month(), command.limit(), now);

        Optional<BudgetId> replaced = current.replaces();
        if (replaced.isPresent()) {
            budgets.replace(current, now);
            audit.record(AuditRecord.budgetChanged(current.id(), replaced.get()));
        } else {
            budgets.set(current);
            audit.record(AuditRecord.budgetSet(current.id()));
        }

        return Outcome.applied(current);
    }

    /**
     * El límite que se pidió se construye solo para compararlo, igual que al
     * abrir una cuenta: es la forma de que la comparación aplique las mismas
     * reglas que tendría si se fuera a guardar.
     */
    private static Outcome<Budget> sameOrConflict(
            Budget stored, SetBudgetCommand command, Account category) {

        Budget asked = Budget.of(
                command.id(), category, command.month(), command.limit());

        if (stored.sameContentAs(asked)) {
            return Outcome.alreadyApplied(stored);
        }
        throw new DuplicateBudgetException(command.id());
    }
}

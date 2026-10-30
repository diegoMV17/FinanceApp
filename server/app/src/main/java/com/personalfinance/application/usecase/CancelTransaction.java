package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.CancelTransactionCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.Ledger;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionAlreadyCancelledException;
import com.personalfinance.domain.ledger.TransactionSource;
import com.personalfinance.domain.ledger.UnknownTransactionException;
import com.personalfinance.domain.ledger.port.TransactionStore;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU07. Anular un movimiento: deja de contar y se queda en la historia.
 *
 * <p>Anular dos veces con el mismo motivo es un reintento y no escribe nada.
 * Anular algo que ya estaba anulado de otra forma — con otro motivo, o porque
 * una corrección la reemplazó — es una contradicción, y falla.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class CancelTransaction {

    private final BookLock lock;
    private final BookLoader loader;
    private final TransactionStore transactions;
    private final AuditTrail audit;
    private final Clock clock;

    CancelTransaction(
            BookLock lock,
            BookLoader loader,
            TransactionStore transactions,
            AuditTrail audit,
            Clock clock) {

        this.lock = lock;
        this.loader = loader;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * @return cómo quedó anulado el movimiento: la anulación que acabó de
     *         escribirse, o la que ya estaba si este era un reintento idéntico
     * @throws com.personalfinance.domain.ledger.TransactionAlreadyCancelledException
     *         si ya estaba anulado de otra forma
     * @throws com.personalfinance.domain.ledger.UnknownTransactionException
     *         si el libro no tiene ese movimiento
     */
    @Transactional
    public Outcome<Cancellation> execute(CancelTransactionCommand command) {
        lock.acquire();
        Ledger ledger = loader.load().ledger();

        Transaction target = ledger.find(command.id())
                .orElseThrow(() -> new UnknownTransactionException(command.id()));

        Optional<Cancellation> already = target.cancellation();
        if (already.isPresent()) {
            return sameOrConflict(target, already.get(), command.reason());
        }

        Cancellation how = ledger.cancel(command.id(), clock.instant(), command.reason())
                .cancellation()
                .orElseThrow();
        transactions.markCancelled(command.id(), how);
        audit.record(AuditRecord.movementCancelled(command.id()));

        return Outcome.applied(how);
    }

    /**
     * Anular lo ya anulado con el mismo motivo es el mismo reintento y devuelve
     * la anulación que había. Con otro motivo, o si lo que la anuló fue una
     * corrección, son dos verdades distintas y la primera es la que queda.
     *
     * <p>La comparación se arma con la fecha de la que ya está guardada a
     * propósito: lo que se compara es el motivo y el origen, no cuándo se pidió.
     * Y se arma con {@link Cancellation} y no a mano para que el motivo pase por
     * el mismo recorte y los mismos límites que el día que se guardó.
     */
    private static Outcome<Cancellation> sameOrConflict(
            Transaction target, Cancellation already, String reason) {

        Cancellation asked = new Cancellation(already.at(), reason, TransactionSource.APP);
        if (asked.equals(already)) {
            return Outcome.alreadyApplied(already);
        }
        throw new TransactionAlreadyCancelledException(target);
    }
}

package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.CancelTransactionCommand;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.port.TransactionStore;
import java.time.Clock;
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
        throw new UnsupportedOperationException("paso 4");
    }
}

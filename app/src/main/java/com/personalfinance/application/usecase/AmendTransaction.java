package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.AmendTransactionCommand;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.port.TransactionStore;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU06. Corregir un movimiento.
 *
 * <p>Las dos mitades — la original deja de contar, la corrección empieza a
 * contar — se guardan con {@code TransactionStore.amend}, en un solo commit.
 * Nunca {@code markCancelled} y después {@code record}: si falla la segunda
 * mitad, el movimiento desaparece de todos los saldos.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class AmendTransaction {

    private final BookLock lock;
    private final BookLoader loader;
    private final TransactionStore transactions;
    private final AuditTrail audit;
    private final Clock clock;

    AmendTransaction(
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
     * @return la corrección que quedó en el libro, la que acabó de escribirse o
     *         la que ya estaba si este era un reintento idéntico
     * @throws com.personalfinance.domain.ledger.DuplicateTransactionException
     *         si el id de la corrección ya está usado por otra cosa
     * @throws com.personalfinance.domain.ledger.TransactionAlreadyCancelledException
     *         si la original ya no cuenta
     */
    @Transactional
    public Outcome<Transaction> execute(AmendTransactionCommand command) {
        throw new UnsupportedOperationException("paso 4");
    }
}

package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.DuplicateTransactionException;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.port.TransactionStore;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU04, HU05, HU10, HU12-HU20. Registrar un movimiento.
 *
 * <p>La única vía por la que algo externo escribe en el libro, junto con sus
 * cinco hermanos. Todos siguen la misma forma, en una sola transacción: tomar
 * el turno, cargar el libro, mirar si esto ya estaba, aplicar la regla en el
 * dominio, persistir por el puerto y anotar en la bitácora.
 *
 * <p>El aislamiento es el de Postgres por omisión, READ COMMITTED, y eso es
 * deliberado: en REPEATABLE READ la foto se tomaría al empezar a esperar el
 * candado, así que el libro que se carga no vería lo que la escritura anterior
 * acabó de guardar.
 */
@Service
public class RecordMovement {

    private final BookLock lock;
    private final BookLoader loader;
    private final TransactionStore transactions;
    private final AuditTrail audit;

    RecordMovement(
            BookLock lock,
            BookLoader loader,
            TransactionStore transactions,
            AuditTrail audit) {

        this.lock = lock;
        this.loader = loader;
        this.transactions = transactions;
        this.audit = audit;
    }

    /**
     * @return el movimiento que quedó en el libro: el que acabó de escribirse,
     *         o el que ya estaba cuando este era un reintento idéntico
     * @throws com.personalfinance.domain.ledger.DuplicateTransactionException
     *         si ese id ya está en el libro con otro contenido
     */
    @Transactional
    public Outcome<Transaction> execute(MovementCommand command) {
        lock.acquire();
        Book book = loader.load();

        // Armar el movimiento antes de mirar si ya estaba: es lo que resuelve
        // las cuentas contra el catálogo y aplica las reglas del dominio, y sin
        // eso no hay con qué comparar.
        Transaction movement = command.toTransaction(book.chart());

        Optional<Transaction> stored = book.ledger().find(movement.id());
        if (stored.isPresent()) {
            return sameOrConflict(stored.get(), movement);
        }

        book.ledger().record(movement);
        transactions.record(movement);
        audit.record(AuditRecord.movementRecorded(movement.id()));

        return Outcome.applied(movement);
    }

    /**
     * Un reintento idéntico devuelve lo que ya estaba y no escribe nada,
     * auditoría incluida. Cualquier otra cosa bajo ese id es una
     * contradicción, y entonces no se devuelve: se lanza.
     *
     * <p>Una corrección no cuenta como "lo mismo" ni con idéntico contenido:
     * registrar un movimiento y corregir otro son dos cosas distintas, y el id
     * de una corrección ya está ocupado por ella.
     */
    private static Outcome<Transaction> sameOrConflict(Transaction stored, Transaction asked) {
        if (stored.replaces().isEmpty() && stored.sameContentAs(asked)) {
            return Outcome.alreadyApplied(stored);
        }
        throw new DuplicateTransactionException(asked.id());
    }
}

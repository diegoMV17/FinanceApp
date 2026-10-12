package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.AmendTransactionCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.DuplicateTransactionException;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.UnknownTransactionException;
import com.personalfinance.domain.ledger.port.TransactionStore;
import java.time.Clock;
import java.util.Optional;
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
        lock.acquire();
        Book book = loader.load();

        Transaction correction = command.correction().toTransaction(book.chart());

        Optional<Transaction> stored = book.ledger().find(correction.id());
        if (stored.isPresent()) {
            return sameOrConflict(stored.get(), correction, command.originalId());
        }

        Transaction original = book.ledger().find(command.originalId())
                .orElseThrow(() -> new UnknownTransactionException(command.originalId()));

        Transaction replacement =
                book.ledger().amend(command.originalId(), correction, clock.instant());

        // Las dos mitades en una sola llamada, que es un solo commit: anular y
        // registrar por separado deja el movimiento desaparecido si falla la
        // segunda.
        transactions.amend(replacement, original.cancellation().orElseThrow());
        audit.record(AuditRecord.movementAmended(command.originalId(), replacement.id()));

        return Outcome.applied(replacement);
    }

    /**
     * El reintento de una corrección tiene que coincidir en dos cosas: el
     * contenido y a quién reemplaza. La segunda importa porque el mismo id
     * corrigiendo otro movimiento es otra corrección, y aplicarla dejaría dos
     * versiones vivas del mismo gasto.
     */
    private static Outcome<Transaction> sameOrConflict(
            Transaction stored, Transaction asked, TransactionId originalId) {

        boolean replacesTheSame = stored.replaces().filter(originalId::equals).isPresent();
        if (replacesTheSame && stored.sameContentAs(asked)) {
            return Outcome.alreadyApplied(stored);
        }
        throw new DuplicateTransactionException(asked.id());
    }
}

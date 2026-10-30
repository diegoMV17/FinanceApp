package com.personalfinance.infrastructure.persistence;

import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.Chart;
import com.personalfinance.domain.ledger.DuplicateTransactionException;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionAlreadyCancelledException;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.UnknownTransactionException;
import com.personalfinance.domain.ledger.port.TransactionStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * El libro, contra Postgres.
 *
 * <p>Una transacción y sus apuntes se escriben en un solo commit. No es un
 * detalle de eficiencia: el trigger que exige que los apuntes sumen cero es
 * {@code DEFERRABLE INITIALLY DEFERRED} y corre al cerrar la transacción de base
 * de datos. Apunte por apunte en autocommit, vería siempre un solo apunte y
 * rechazaría todo.
 *
 * <p>El encabezado va primero y con {@code flush}: el apunte tiene una clave
 * ajena a {@code transactions}, y sin declarar la relación en el mapeo
 * Hibernate no tiene cómo adivinar ese orden. Los apuntes también se escriben
 * con {@code flush}, para que un error salga aquí, en la llamada que lo causó,
 * y no al cerrar la transacción con una traza que no dice de dónde vino.
 *
 * <p>Lo que el dominio rechaza en memoria — registrar dos veces, anular dos
 * veces — también se rechaza aquí, con las mismas excepciones. El libro en
 * memoria y la base pueden no estar sincronizados (otro dispositivo, un
 * reintento de red), así que esta capa no puede confiar en que el dominio ya
 * lo revisó.
 */
@Repository
class JpaTransactionStore implements TransactionStore {

    private final TransactionRowRepository transactions;
    private final EntryRowRepository entries;

    JpaTransactionStore(TransactionRowRepository transactions, EntryRowRepository entries) {
        this.transactions = transactions;
        this.entries = entries;
    }

    @Override
    @Transactional
    public void record(Transaction transaction) {
        if (transactions.existsById(transaction.id().value())) {
            throw new DuplicateTransactionException(transaction.id());
        }
        transactions.saveAndFlush(LedgerMapper.rowOf(transaction));
        entries.saveAllAndFlush(LedgerMapper.rowsOf(transaction));
    }

    @Override
    @Transactional
    public void markCancelled(TransactionId id, Cancellation cancellation) {
        TransactionRow row = require(id);
        if (row.isCancelled()) {
            throw new TransactionAlreadyCancelledException(id, row.cancelledAt());
        }
        row.cancel(cancellation.at(), cancellation.reason(), cancellation.source());
    }

    /**
     * Las dos mitades de una corrección dentro del mismo {@code @Transactional}:
     * si la segunda falla, la primera se deshace con ella. Las llamadas internas
     * no pasan por el proxy de Spring, así que no abren transacciones propias;
     * corren dentro de esta.
     */
    @Override
    @Transactional
    public void amend(Transaction replacement, Cancellation ofOriginal) {
        TransactionId original = replacement.replaces().orElseThrow(() ->
                new IllegalArgumentException(
                        "Transaction %s does not replace any other".formatted(replacement.id())));

        markCancelled(original, ofOriginal);
        record(replacement);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> find(TransactionId id, Chart chart) {
        return transactions.findById(id.value())
                .map(row -> LedgerMapper.transactionFrom(
                        row, entries.findAllByTransactionId(row.getId()), chart));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> all(Chart chart) {
        List<TransactionRow> rows = transactions.findAllByOrderByRecordedAtAscIdAsc();
        if (rows.isEmpty()) {
            return List.of();
        }

        // Todos los apuntes de una vez. Una consulta por transacción sería una
        // consulta por movimiento del historial completo.
        Map<UUID, List<EntryRow>> byTransaction =
                entries.findAllByTransactionIdIn(rows.stream().map(TransactionRow::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(EntryRow::transactionId));

        return rows.stream()
                .map(row -> LedgerMapper.transactionFrom(
                        row, byTransaction.getOrDefault(row.getId(), List.of()), chart))
                .toList();
    }

    /** Devuelve la fila gestionada: cambiarla ya es el UPDATE, al cerrar. */
    private TransactionRow require(TransactionId id) {
        return transactions.findById(id.value())
                .orElseThrow(() -> new UnknownTransactionException(id));
    }
}

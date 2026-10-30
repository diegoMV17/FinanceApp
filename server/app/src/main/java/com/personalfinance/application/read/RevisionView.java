package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.TransactionSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Una versión de un movimiento, con todo lo que la explica: qué decía, si dejó
 * de contar, por qué, y a cuál versión reemplazó.
 *
 * <p>Es la respuesta a "¿qué decía esto antes?". Aquí sí van los apuntes
 * completos, porque la pregunta es exactamente qué cambió entre una versión y
 * la siguiente.
 *
 * @param cancelledAt        cuándo dejó de contar, o {@code null} si todavía cuenta
 * @param cancellationReason por qué, o {@code null}
 * @param replaces           la versión que reemplazó, o {@code null}
 */
public record RevisionView(
        TransactionId id,
        LocalDate date,
        String description,
        TransactionSource source,
        List<EntryView> entries,
        Instant cancelledAt,
        String cancellationReason,
        TransactionId replaces) {

    public RevisionView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(source, "source");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    static RevisionView of(Transaction transaction) {
        Optional<Cancellation> cancellation = transaction.cancellation();

        return new RevisionView(
                transaction.id(),
                transaction.date(),
                transaction.description(),
                transaction.source(),
                transaction.entries().stream().map(EntryView::of).toList(),
                cancellation.map(Cancellation::at).orElse(null),
                cancellation.map(Cancellation::reason).orElse(null),
                transaction.replaces().orElse(null));
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }
}

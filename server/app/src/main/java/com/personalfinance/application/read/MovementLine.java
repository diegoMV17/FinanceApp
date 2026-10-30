package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.TransactionSource;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * HU24, HU25. Un movimiento del historial general.
 *
 * <p>No es lo mismo que {@link MovementView}, y la diferencia no es de forma
 * sino de pregunta. Un {@code MovementView} contesta «cómo movió esto la plata
 * de mi mamá», que exige una cuenta desde la que mirar; el historial general no
 * tiene ninguna, así que un «efecto» ahí no significaría nada.
 *
 * <p>Por eso lleva sus apuntes: sin la cuenta desde la que se mira, lo único
 * que explica un movimiento es de dónde salió y a dónde fue. Y lleva su valor,
 * que es por lo que HU25 permite filtrar: un cliente que pidió «entre 30.000 y
 * 50.000» tiene que poder ver el número con el que se le respondió.
 *
 * @param value la magnitud del movimiento, siempre cero o más
 */
public record MovementLine(
        TransactionId id,
        LocalDate date,
        String description,
        TransactionSource source,
        Money value,
        List<EntryView> entries) {

    public MovementLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(value, "value");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    static MovementLine of(Transaction movement) {
        return new MovementLine(
                movement.id(),
                movement.date(),
                movement.description(),
                movement.source(),
                movement.magnitude(),
                movement.entries().stream().map(EntryView::of).toList());
    }
}

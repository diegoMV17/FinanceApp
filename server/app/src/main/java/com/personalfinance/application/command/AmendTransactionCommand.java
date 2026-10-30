package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.TransactionId;
import java.util.Objects;

/**
 * HU06. Corregir un movimiento: no se edita nada, el viejo deja de contar y la
 * corrección queda en su lugar apuntando a él.
 *
 * <p>La corrección es un comando de movimiento igual que cualquier otro, con su
 * propio id. Ese id es el que hace reconocible el reintento de una corrección:
 * sin él, enviarla dos veces produciría dos correcciones distintas.
 */
public record AmendTransactionCommand(TransactionId originalId, MovementCommand correction) {

    public AmendTransactionCommand {
        Objects.requireNonNull(originalId, "originalId");
        Objects.requireNonNull(correction, "correction");
    }
}

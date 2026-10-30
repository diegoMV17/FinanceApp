package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.TransactionId;
import java.util.Objects;

/**
 * HU07. Dejar de contar un movimiento, sin borrarlo.
 *
 * <p>El motivo es parte de la identidad de la anulación: anular dos veces el
 * mismo movimiento con el mismo motivo es un reintento, y con otro motivo es
 * una contradicción. El largo del motivo lo acota {@code Cancellation}, en el
 * dominio.
 */
public record CancelTransactionCommand(TransactionId id, String reason) {

    public CancelTransactionCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
    }
}

package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.TransactionSource;
import java.time.Instant;
import java.util.UUID;

/**
 * Una anulación.
 *
 * <p>El id del movimiento se pasa aparte porque {@link Cancellation} no lo
 * trae: en el dominio una anulación vive dentro de su transacción y no necesita
 * nombrarla. Por HTTP sí hace falta, y sale de la ruta que se pidió.
 */
public record CancellationResponse(
        UUID movement, Instant at, String reason, TransactionSource source) {

    public static CancellationResponse of(UUID movement, Cancellation cancellation) {
        return new CancellationResponse(
                movement, cancellation.at(), cancellation.reason(), cancellation.source());
    }
}

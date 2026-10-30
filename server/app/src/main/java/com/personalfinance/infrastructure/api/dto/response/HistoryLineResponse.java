package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.MovementLine;
import com.personalfinance.domain.ledger.TransactionSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un movimiento del historial general: qué fue, cuánto valió y qué cuentas tocó.
 *
 * <p>HU24, HU25. No es {@link MovementLineResponse}, que es la misma cosa vista
 * desde una cuenta y por eso trae un {@code effectInCents}. Aquí no hay cuenta
 * desde la que mirar, así que lo que explica el movimiento son sus apuntes.
 *
 * <p>El valor es la magnitud: la suma de los apuntes positivos, que en un
 * asiento que suma cero es el número que una persona diría en voz alta. Va en la
 * respuesta porque es el campo por el que HU25 deja filtrar, y un cliente que
 * pidió un rango tiene que poder ver con qué número se le contestó.
 */
public record HistoryLineResponse(
        UUID id,
        LocalDate date,
        String description,
        TransactionSource source,
        long valueInCents,
        List<EntryResponse> entries) {

    public static HistoryLineResponse of(MovementLine movement) {
        return new HistoryLineResponse(
                movement.id().value(),
                movement.date(),
                movement.description(),
                movement.source(),
                movement.value().cents(),
                movement.entries().stream().map(EntryResponse::of).toList());
    }
}

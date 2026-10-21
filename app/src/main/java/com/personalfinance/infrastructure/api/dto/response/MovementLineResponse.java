package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.MovementView;
import com.personalfinance.domain.ledger.TransactionSource;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una línea del extracto de una cuenta.
 *
 * <p>Sin apuntes: lo que interesa aquí es el efecto sobre la cuenta que se
 * pidió, que es lo que {@link MovementView} calcula. Quien quiera el asiento
 * completo pide las revisiones del movimiento.
 */
public record MovementLineResponse(
        UUID id,
        LocalDate date,
        String description,
        TransactionSource source,
        long effectInCents) {

    public static MovementLineResponse of(MovementView movement) {
        return new MovementLineResponse(
                movement.id().value(),
                movement.date(),
                movement.description(),
                movement.source(),
                movement.effect().cents());
    }
}

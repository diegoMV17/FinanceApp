package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.AmendTransactionCommand;
import com.personalfinance.domain.ledger.TransactionId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Corregir un movimiento.
 *
 * <p>La corrección trae su propio id, distinto del que se corrige: el original
 * queda anulado y la corrección se registra apuntando a él. Nada se borra.
 *
 * <p>El {@code @Valid} de aquí no es decorativo. Sin él, el movimiento anidado
 * no se valida en absoluto: ni los largos, ni los montos, ni el texto. Es una
 * palabra que al faltar no da ningún error de compilación y abre un agujero
 * exactamente del tamaño de todas las validaciones del borde.
 */
public record AmendMovementRequest(@NotNull @Valid MovementRequest correction) {

    public AmendTransactionCommand toCommand(UUID originalId) {
        return new AmendTransactionCommand(
                new TransactionId(originalId), correction.toCommand());
    }
}

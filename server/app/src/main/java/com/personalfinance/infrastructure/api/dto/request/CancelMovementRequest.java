package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.CancelTransactionCommand;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.infrastructure.api.validation.SafeText;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Anular un movimiento.
 *
 * <p>El motivo se exige presente pero se admite vacío, porque es lo que admite
 * el dominio. El borde valida tipos, formatos y largos; inventar aquí la regla
 * de que un motivo no puede estar en blanco sería una regla de negocio
 * escondida en un DTO, y el dia que el dominio opine distinto habría dos
 * verdades.
 */
public record CancelMovementRequest(
        @NotNull @Size(max = Cancellation.MAX_REASON_LENGTH) @SafeText String reason) {

    public CancelTransactionCommand toCommand(UUID movementId) {
        return new CancelTransactionCommand(new TransactionId(movementId), reason);
    }
}

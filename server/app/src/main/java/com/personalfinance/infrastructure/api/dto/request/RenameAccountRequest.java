package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.RenameAccountCommand;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.infrastructure.api.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cambiarle el nombre a una cuenta.
 *
 * <p>Sin el id en el cuerpo: viene de la ruta. Tenerlo en los dos sitios
 * obligaría a decidir qué hacer cuando no coinciden, y la respuesta correcta a
 * esa pregunta es no hacérsela.
 */
public record RenameAccountRequest(
        @NotBlank @Size(max = Account.MAX_NAME_LENGTH) @SafeText String newName) {

    public RenameAccountCommand toCommand(UUID accountId) {
        return new RenameAccountCommand(new AccountId(accountId), newName);
    }
}

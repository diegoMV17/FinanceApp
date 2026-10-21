package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.OpenAccountCommand;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.infrastructure.api.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Abrir una cuenta.
 *
 * <p>El id lo genera el cliente, que es lo que hace el reintento seguro: mandar
 * dos veces la misma petición deja una sola cuenta.
 *
 * <p>El {@code type} es el enum del dominio y no una copia. No es un descuido:
 * los siete tipos de cuenta <em>son</em> el vocabulario del contrato, y un enum
 * paralelo sería una segunda lista que alguien tendria que acordarse de
 * actualizar. Un valor que no exista en el enum no deserializa, y eso es un 400.
 */
public record OpenAccountRequest(
        @NotNull UUID id,
        @NotNull AccountType type,
        @NotBlank @Size(max = Account.MAX_NAME_LENGTH) @SafeText String name) {

    public OpenAccountCommand toCommand() {
        return new OpenAccountCommand(new AccountId(id), type, name);
    }
}

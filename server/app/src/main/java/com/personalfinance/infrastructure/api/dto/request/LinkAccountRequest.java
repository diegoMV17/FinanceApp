package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.LinkAccountToPersonCommand;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.PersonId;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Decir que esta cuenta es de la persona de la ruta.
 *
 * <p>La cuenta es obligatoria y es lo único que lleva el cuerpo. No hay forma de
 * expresar «quítale la persona a esta cuenta», y eso es la mitad de la regla:
 * equivocarse de persona se corrige mandando esta misma petición a la otra, y
 * borrar el vínculo borraría un hecho ya declarado en un libro donde nada se
 * borra. La otra mitad la pone el trigger de la V6.
 */
public record LinkAccountRequest(@NotNull UUID account) {

    public LinkAccountToPersonCommand toCommand(UUID personId) {
        return new LinkAccountToPersonCommand(new AccountId(account), new PersonId(personId));
    }
}

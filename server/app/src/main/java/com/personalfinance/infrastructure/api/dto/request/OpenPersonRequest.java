package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.OpenPersonCommand;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.infrastructure.api.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Escribir a alguien en el directorio.
 *
 * <p>El id lo genera el cliente, que es lo que hace el reintento seguro: mandar
 * dos veces la misma petición deja una sola persona.
 *
 * <p>No lleva las cuentas de la persona. Vincularlas es otra petición, y no por
 * pereza: el libro de hoy ya tiene cuentas de gente, abiertas desde antes de que
 * hubiera personas, así que vincular después es el camino principal y no un
 * extra. Una sola forma de vincular es también una sola rama que probar.
 */
public record OpenPersonRequest(
        @NotNull UUID id,
        @NotBlank @Size(max = Person.MAX_NAME_LENGTH) @SafeText String name) {

    public OpenPersonCommand toCommand() {
        return new OpenPersonCommand(new PersonId(id), name);
    }
}

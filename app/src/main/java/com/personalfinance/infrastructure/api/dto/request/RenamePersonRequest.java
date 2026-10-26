package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.RenamePersonCommand;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.infrastructure.api.validation.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cambiarle el nombre a una persona.
 *
 * <p>Sin el id en el cuerpo: viene de la ruta. Tenerlo en los dos sitios
 * obligaría a decidir qué hacer cuando no coinciden, y la respuesta correcta a
 * esa pregunta es no hacérsela.
 */
public record RenamePersonRequest(
        @NotBlank @Size(max = Person.MAX_NAME_LENGTH) @SafeText String newName) {

    public RenamePersonCommand toCommand(UUID personId) {
        return new RenamePersonCommand(new PersonId(personId), newName);
    }
}

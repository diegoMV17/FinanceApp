package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.PersonId;
import java.util.Objects;

/**
 * HU19. Decir de quién es una cuenta, que es lo que permite leer tres cuentas
 * como una persona.
 *
 * <p>La persona es obligatoria: no hay forma de expresar «ya no es de nadie».
 * Equivocarse de persona se corrige mandando la otra; borrar el vínculo
 * borraría un hecho que alguien ya declaró, en un libro donde nada se borra.
 */
public record LinkAccountToPersonCommand(AccountId account, PersonId person) {

    public LinkAccountToPersonCommand {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(person, "person");
    }
}

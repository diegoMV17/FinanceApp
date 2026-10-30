package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.AccountId;
import java.util.Objects;

/**
 * Cambiarle el nombre a una cuenta. El tipo no se toca nunca: toda su historia
 * se lee según el tipo, y cambiarlo convertiría cada depósito en un gasto.
 */
public record RenameAccountCommand(AccountId id, String newName) {

    public RenameAccountCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(newName, "newName");
    }
}

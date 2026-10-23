package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.PersonId;
import java.util.Objects;

/** Cambiarle el nombre a una persona. Es lo único de ella que cambia. */
public record RenamePersonCommand(PersonId id, String newName) {

    public RenamePersonCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(newName, "newName");
    }
}

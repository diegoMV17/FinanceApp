package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.PersonId;
import java.util.Objects;

/**
 * Retirar a una persona de las listas donde se elige, dejándola en las que se
 * lee historia. Una persona con cuentas abiertas no se archiva.
 */
public record ArchivePersonCommand(PersonId id) {

    public ArchivePersonCommand {
        Objects.requireNonNull(id, "id");
    }
}

package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.AccountId;
import java.util.Objects;

/**
 * HU11. Retirar una cuenta de las listas donde se elige, dejándola en las que
 * se lee historia. Una cuenta que todavía tiene plata no se archiva.
 */
public record ArchiveAccountCommand(AccountId id) {

    public ArchiveAccountCommand {
        Objects.requireNonNull(id, "id");
    }
}

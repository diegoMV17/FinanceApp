package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import java.util.Objects;

/**
 * HU08. Abrir una cuenta, una persona o una categoría.
 *
 * <p>El id lo trae el cliente, igual que en los movimientos: el teléfono puede
 * crear la cuenta sin red y enviarla después. El largo del nombre lo acota
 * {@code Account}, en el dominio.
 */
public record OpenAccountCommand(AccountId id, AccountType type, String name) {

    public OpenAccountCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
    }
}

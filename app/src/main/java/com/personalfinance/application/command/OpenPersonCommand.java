package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.PersonId;
import java.util.Objects;

/**
 * HU19. Escribir a alguien en el directorio: a quién le debes, quién te debe, o
 * de quién guardas plata.
 *
 * <p>El id lo trae el cliente, igual que en las cuentas y los movimientos: el
 * teléfono puede escribirla sin red y enviarla después. El largo del nombre lo
 * acota {@code Person}, en el dominio.
 *
 * <p>No dice nada de sus cuentas. Una persona no nace con cuentas: vincularlas
 * es otra operación, porque el libro de hoy ya tiene cuentas de gente que hay
 * que vincular después.
 */
public record OpenPersonCommand(PersonId id, String name) {

    public OpenPersonCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}

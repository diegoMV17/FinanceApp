package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * HU25. Lo que se pidió filtrar, todavía en ids.
 *
 * <p>Es a {@code MovementQuery} lo que un comando es al dominio: lo que llegó
 * de fuera, con la forma correcta pero sin resolver. Una cuenta y una persona
 * llegan como identificadores, y convertirlos en los objetos que el libro
 * entiende exige el libro cargado — que solo existe dentro de la transacción de
 * una lectura, nunca en un controlador.
 *
 * <p>Esa resolución es además donde se decide algo que importa: filtrar por una
 * cuenta o una persona que no existe responde 404 y no una lista vacía. Una
 * lista vacía se lee como «no hubo movimientos», que es una respuesta
 * perfectamente tranquilizadora a una pregunta mal escrita.
 *
 * <p>Todo es opcional; ausente es sin restricción. {@code accounts} se exige
 * entero: un movimiento tiene que tocar todas, no alguna.
 */
public record MovementFilter(
        LocalDate from,
        LocalDate to,
        AccountType type,
        List<AccountId> accounts,
        PersonId person,
        Money minAmount,
        Money maxAmount) {

    public MovementFilter {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }

    /** Todo el libro. */
    public static MovementFilter unfiltered() {
        return new MovementFilter(null, null, null, List.of(), null, null, null);
    }

    /** Lo que ocurrió en ese rango de fechas, los dos extremos incluidos. */
    public static MovementFilter between(LocalDate from, LocalDate to) {
        return new MovementFilter(from, to, null, List.of(), null, null, null);
    }

    public MovementFilter ofType(AccountType newType) {
        return new MovementFilter(from, to, newType, accounts, person, minAmount, maxAmount);
    }
}

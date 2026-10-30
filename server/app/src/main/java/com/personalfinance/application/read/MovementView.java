package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.TransactionSource;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * HU21, HU24. Un movimiento visto desde una cuenta: cuánto la movió a ella.
 *
 * <p>Esa es la pregunta que se hace frente al extracto de un fondo o de una
 * persona ("¿qué pasó con la plata de mi mamá?"), y la respuesta no es el monto
 * de la transacción — una transacción no tiene monto propio — sino su efecto
 * sobre esa cuenta.
 */
public record MovementView(
        TransactionId id,
        LocalDate date,
        String description,
        TransactionSource source,
        Money effect) {

    public MovementView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(effect, "effect");
    }

    static MovementView of(Transaction transaction, Account seenFrom) {
        return new MovementView(
                transaction.id(),
                transaction.date(),
                transaction.description(),
                transaction.source(),
                transaction.effectOn(seenFrom));
    }
}

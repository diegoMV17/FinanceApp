package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.application.command.PayCardCommand;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.Entry;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import com.personalfinance.infrastructure.api.validation.SafeText;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * El pago de una tarjeta de crédito: sale la plata y baja lo que se le debe.
 * No es un gasto; el gasto fue el día que se pasó la tarjeta.
 *
 * <p>Las mismas reglas del borde que los demás movimientos: los límites apuntan
 * a las constantes del dominio y no hay {@code requireNonNull}, para que un
 * campo que falta sea un 400 y no un 500.
 */
public record PayCardRequest(
        @NotNull UUID id,
        @NotNull LocalDate date,
        @NotNull
        @Size(max = Transaction.MAX_DESCRIPTION_LENGTH)
        @SafeText
        String description,
        @NotNull UUID from,
        @NotNull UUID card,
        @Positive @Max(Entry.MAX_ABSOLUTE_CENTS) long amountInCents)
        implements MovementRequest {

    @Override
    public MovementCommand toCommand() {
        return new PayCardCommand(
                new TransactionId(id),
                date,
                description,
                new AccountId(from),
                new AccountId(card),
                Money.ofCents(amountInCents));
    }
}

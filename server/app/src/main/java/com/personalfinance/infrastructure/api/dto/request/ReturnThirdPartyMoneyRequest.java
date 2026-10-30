package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.application.command.ReturnThirdPartyMoneyCommand;
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
 * La devolución de la plata de otro.
 *
 * <p>Las anotaciones apuntan a las constantes del dominio y no repiten el
 * número: si alguien cambia el límite allá, el borde cambia con él. El dominio
 * sigue siendo la última línea de defensa; esto es lo que permite responder un
 * 400 claro en vez de dejar que reviente más adentro.
 *
 * <p>Sin {@code requireNonNull} en el constructor, a propósito, al contrario
 * que en los comandos del dominio. Jackson construye esto antes de que Bean
 * Validation lo mire, así que estallar aquí convertiría un campo que falta en
 * un 500 en vez del 400 que le corresponde. Lo estricto es el comando, y para
 * cuando se construye ya está todo validado.
 */
public record ReturnThirdPartyMoneyRequest(
        @NotNull UUID id,
        @NotNull LocalDate date,
        @NotNull
        @Size(max = Transaction.MAX_DESCRIPTION_LENGTH)
        @SafeText
        String description,
        @NotNull UUID fund,
        @NotNull UUID paidFrom,
        @Positive @Max(Entry.MAX_ABSOLUTE_CENTS) long amountInCents)
        implements MovementRequest {

    @Override
    public MovementCommand toCommand() {
        return new ReturnThirdPartyMoneyCommand(
                new TransactionId(id),
                date,
                description,
                new AccountId(fund),
                new AccountId(paidFrom),
                Money.ofCents(amountInCents));
    }
}

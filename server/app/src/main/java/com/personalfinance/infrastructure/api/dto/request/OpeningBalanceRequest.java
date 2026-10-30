package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.application.command.OpeningBalanceCommand;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.Entry;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/**
 * El saldo con el que una cuenta entra al libro.
 *
 * <p>El único movimiento sin descripción: la pone la operación, porque
 * siempre dice lo mismo. Mandar una es un 400, no un campo que se ignora.
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
public record OpeningBalanceRequest(
        @NotNull UUID id,
        @NotNull LocalDate date,
        @NotNull UUID account,
        @NotNull UUID equity,
        @Positive @Max(Entry.MAX_ABSOLUTE_CENTS) long amountInCents)
        implements MovementRequest {

    @Override
    public MovementCommand toCommand() {
        return new OpeningBalanceCommand(
                new TransactionId(id),
                date,
                new AccountId(account),
                new AccountId(equity),
                Money.ofCents(amountInCents));
    }
}

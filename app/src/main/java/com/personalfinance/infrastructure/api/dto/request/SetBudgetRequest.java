package com.personalfinance.infrastructure.api.dto.request;

import com.personalfinance.application.command.SetBudgetCommand;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.BudgetId;
import com.personalfinance.domain.ledger.Entry;
import com.personalfinance.domain.shared.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.YearMonth;
import java.util.UUID;

/**
 * HU26. «Este es el límite de esta categoría para este mes.»
 *
 * <p>El mes viaja como texto {@code "2026-03"} y no como un tipo de fecha a
 * propósito. Depender de cómo Jackson decida representar un {@code YearMonth}
 * sería confiar en un valor por omisión que este repositorio ya aprendió a no
 * suponer; así el formato está escrito, validado y es el mismo en la petición y
 * en la respuesta.
 *
 * <p>El patrón acota el mes a 01-12, así que {@code YearMonth.parse} no puede
 * fallar después: si pudiera, un «2026-13» sería un 500 en vez del 400 que le
 * toca.
 *
 * <p>Sin {@code requireNonNull}: Jackson construye esto antes de que Bean
 * Validation lo mire, así que estallar aquí convertiría un campo ausente en un
 * 500 en vez del 400 que le corresponde.
 */
public record SetBudgetRequest(
        @NotNull UUID id,
        @NotNull UUID category,
        @NotNull @Pattern(regexp = MONTH) String month,
        @Positive @Max(Entry.MAX_ABSOLUTE_CENTS) long limitInCents) {

    /**
     * Un mes ISO, con el mes acotado entre 01 y 12.
     *
     * <p>Con clases de caracteres y no con un atajo que lleve barra invertida:
     * el patrón se lee igual y nadie tiene que contar barras entre lo que
     * escapa Java y lo que escapa el regex.
     */
    public static final String MONTH = "[0-9]{4}-(0[1-9]|1[0-2])";

    public SetBudgetCommand toCommand() {
        return new SetBudgetCommand(
                new BudgetId(id),
                new AccountId(category),
                YearMonth.parse(month),
                Money.ofCents(limitInCents));
    }
}

package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.PersonView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HU19, HU21. Cómo está la cuenta con una persona: la respuesta que da sentido
 * a esta aplicación.
 *
 * <p>Los tres lados vienen en positivo, con el signo que lee la persona: 100.000
 * que Juan me debe, 30.000 que le debo, 50.000 de su plata que tengo guardada.
 * {@code netOwedToYouInCents} es lo que quedaría al liquidar todo con él —
 * 20.000 en ese ejemplo — y le resta su fondo por lo mismo que
 * {@code moneyReallyMine}: esa plata está en mi bolsillo pero no es mía, así que
 * tampoco es algo que él me deba.
 *
 * <p>Las cuentas vienen con su saldo, reusando {@link BalanceResponse}: sin
 * ellas el total es un número sin explicación, y la pregunta siguiente de
 * cualquiera es «¿de dónde salen esos 100.000?».
 */
public record PersonPositionResponse(
        UUID id,
        String name,
        Instant archivedAt,
        long owedToYouInCents,
        long youOweInCents,
        long theirFundInCents,
        long netOwedToYouInCents,
        List<BalanceResponse> accounts) {

    public static PersonPositionResponse of(PersonView person) {
        return new PersonPositionResponse(
                person.id().value(),
                person.name(),
                person.archivedAt(),
                person.owedToYou().cents(),
                person.youOwe().cents(),
                person.theirFund().cents(),
                person.netOwedToYou().cents(),
                person.accounts().stream().map(BalanceResponse::of).toList());
    }
}

package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.domain.ledger.Person;
import java.time.Instant;
import java.util.UUID;

/**
 * Una persona, como la ve un cliente. Lo que responden las escrituras.
 *
 * <p>No se devuelve el {@link Person} del dominio: tiene mutadores que solo el
 * agregado debe poder llamar, y serializarlo convertiría su forma interna en el
 * contrato público de la API.
 *
 * <p>No trae sus cuentas ni sus totales: esto responde a «quedó escrita», no a
 * «cómo estamos». Lo segundo es {@link PersonPositionResponse}, que cuesta
 * recorrer el libro entero y por eso vive en las lecturas.
 *
 * <p>El {@code archivedAt} viene nulo cuando sigue abierta, y se escribe igual
 * en el JSON. Omitirlo según el caso daría dos formas para la misma cosa.
 */
public record PersonResponse(UUID id, String name, Instant archivedAt) {

    public static PersonResponse of(Person person) {
        return new PersonResponse(
                person.id().value(), person.name(), person.archivedAt().orElse(null));
    }
}

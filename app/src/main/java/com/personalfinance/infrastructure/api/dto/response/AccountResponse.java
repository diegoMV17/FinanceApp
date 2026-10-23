package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountType;
import java.time.Instant;
import java.util.UUID;

/**
 * Una cuenta, como la ve un cliente.
 *
 * <p>No se devuelve el {@link Account} del dominio: tiene mutadores que solo el
 * agregado debe poder llamar, y serializarlo convertiría su forma interna en el
 * contrato público de la API. Es la misma razón por la que {@code ReadBook} no
 * devuelve objetos del dominio.
 *
 * <p>El {@code archivedAt} viene nulo cuando la cuenta está abierta, y se
 * escribe igual en el JSON. Omitirlo según el caso daría dos formas distintas
 * para la misma cosa, y un cliente tendría que distinguir «no vino» de «vino
 * vacío».
 */
public record AccountResponse(
        UUID id, AccountType type, String name, Instant archivedAt) {

    public static AccountResponse of(Account account) {
        return new AccountResponse(
                account.id().value(),
                account.type(),
                account.name(),
                account.archivedAt().orElse(null));
    }
}

package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.AccountBalance;
import com.personalfinance.domain.ledger.AccountType;
import java.time.Instant;
import java.util.UUID;

/**
 * El saldo de una cuenta.
 *
 * <p>El monto viene ya con el signo de presentación que puso
 * {@link AccountBalance}: lo que el dueño espera leer, no el signo interno del
 * asiento.
 */
public record BalanceResponse(
        UUID id,
        AccountType type,
        String name,
        long balanceInCents,
        Instant archivedAt) {

    public static BalanceResponse of(AccountBalance balance) {
        return new BalanceResponse(
                balance.id().value(),
                balance.type(),
                balance.name(),
                balance.balance().cents(),
                balance.archivedAt());
    }
}

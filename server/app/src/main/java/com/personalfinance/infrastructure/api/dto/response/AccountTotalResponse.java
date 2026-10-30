package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.AccountTotal;
import com.personalfinance.domain.ledger.AccountType;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuánto movió una cuenta en el periodo.
 *
 * <p>HU23. Con {@code type=EXPENSE} esta es una porción del gráfico de gastos
 * por categoría. El campo se llama {@code totalInCents} y no
 * {@code balanceInCents} a propósito: un saldo viene del origen del tiempo y
 * esto es lo que pasó entre dos fechas, y decir que Alimentación «tiene un
 * saldo de 350.000» no significa nada.
 */
public record AccountTotalResponse(
        UUID id, AccountType type, String name, long totalInCents, Instant archivedAt) {

    public static AccountTotalResponse of(AccountTotal total) {
        return new AccountTotalResponse(
                total.id().value(),
                total.type(),
                total.name(),
                total.total().cents(),
                total.archivedAt());
    }
}

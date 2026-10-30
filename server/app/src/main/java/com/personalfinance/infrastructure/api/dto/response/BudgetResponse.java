package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.domain.ledger.Budget;
import com.personalfinance.domain.ledger.BudgetId;
import java.util.UUID;

/**
 * El límite que quedó puesto.
 *
 * <p>HU26. Es la respuesta de la escritura y por eso solo trae el hecho: qué
 * categoría, qué mes, cuánto, y a cuál reemplazó si reemplazó a alguno. Cómo va
 * el mes es otra pregunta y la contesta {@link BudgetProgressResponse}, por lo
 * mismo que abrir una cuenta devuelve un {@link AccountResponse} y no su saldo.
 */
public record BudgetResponse(
        UUID id,
        UUID category,
        String categoryName,
        String month,
        long limitInCents,
        UUID replaces) {

    public static BudgetResponse of(Budget budget) {
        return new BudgetResponse(
                budget.id().value(),
                budget.category().id().value(),
                budget.category().name(),
                budget.month().toString(),
                budget.limit().cents(),
                budget.replaces().map(BudgetId::value).orElse(null));
    }
}

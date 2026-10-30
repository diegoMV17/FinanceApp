package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.BudgetView;
import java.util.UUID;

/**
 * Cómo va una categoría contra su límite.
 *
 * <p>HU27. Los tres derivados vienen decididos: el porcentaje,
 * {@code nearTheLimit} y {@code exceeded}. Que viajen en lugar de dejar que el
 * cliente los deduzca de los montos es lo que impide que el teléfono los
 * recalcule en Dart y que un día el servidor y la pantalla avisen en días
 * distintos del mismo mes.
 *
 * <p>{@code remainingInCents} se vuelve negativo cuando el límite ya se pasó,
 * que es la forma honesta de decir cuánto se pasó.
 */
public record BudgetProgressResponse(
        UUID id,
        UUID category,
        String categoryName,
        String month,
        long limitInCents,
        long spentInCents,
        long remainingInCents,
        long percentageUsed,
        boolean nearTheLimit,
        boolean exceeded) {

    public static BudgetProgressResponse of(BudgetView budget) {
        return new BudgetProgressResponse(
                budget.id().value(),
                budget.category().value(),
                budget.categoryName(),
                budget.month().toString(),
                budget.limit().cents(),
                budget.spent().cents(),
                budget.remaining().cents(),
                budget.percentageUsed(),
                budget.nearTheLimit(),
                budget.exceeded());
    }
}

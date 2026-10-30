package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.BudgetId;
import com.personalfinance.domain.ledger.BudgetProgress;
import com.personalfinance.domain.shared.Money;
import java.time.YearMonth;
import java.util.Objects;

/**
 * HU27. Cómo va un mes contra su límite.
 *
 * <p>Los tres derivados — el porcentaje, «se está acercando» y «se pasó» — vienen
 * calculados del dominio y viajan en la respuesta. No es información de más: es
 * lo que impide que el teléfono los recalcule en Dart y que un día el servidor
 * y la pantalla avisen en días distintos del mismo mes.
 */
public record BudgetView(
        BudgetId id,
        AccountId category,
        String categoryName,
        YearMonth month,
        Money limit,
        Money spent,
        Money remaining,
        long percentageUsed,
        boolean nearTheLimit,
        boolean exceeded) {

    public BudgetView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(categoryName, "categoryName");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(limit, "limit");
        Objects.requireNonNull(spent, "spent");
        Objects.requireNonNull(remaining, "remaining");
    }

    static BudgetView of(BudgetProgress progress) {
        return new BudgetView(
                progress.budget(),
                progress.category(),
                progress.categoryName(),
                progress.month(),
                progress.limit(),
                progress.spent(),
                progress.remaining(),
                progress.percentageUsed(),
                progress.isNearTheLimit(),
                progress.isExceeded());
    }
}

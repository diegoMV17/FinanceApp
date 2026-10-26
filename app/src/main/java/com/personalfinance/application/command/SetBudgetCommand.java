package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.BudgetId;
import com.personalfinance.domain.shared.Money;
import java.time.YearMonth;
import java.util.Objects;

/**
 * HU26. Este es el límite de esta categoría para este mes.
 *
 * <p>El id lo pone el cliente, como en todas las escrituras: es lo que permite
 * reconocer un reintento después de un corte de red en vez de registrar el
 * mismo límite dos veces.
 */
public record SetBudgetCommand(
        BudgetId id, AccountId category, YearMonth month, Money limit) {

    public SetBudgetCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(limit, "limit");
    }
}

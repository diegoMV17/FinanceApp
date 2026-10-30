package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.BudgetView;
import java.util.List;

/**
 * Los límites de un mes, en sobre con el mes que se contestó.
 *
 * <p>HU27. El mes viaja de vuelta porque cuando no se manda lo elige el
 * servidor — el mes de hoy, en el calendario del dueño —, exactamente como el
 * {@code asOf} de las demás lecturas.
 *
 * <p>Vienen con el más consumido primero: arriba lo que está a punto de
 * reventar.
 */
public record BudgetsResponse(String month, List<BudgetProgressResponse> budgets) {

    public static BudgetsResponse of(String month, List<BudgetView> budgets) {
        return new BudgetsResponse(
                month, budgets.stream().map(BudgetProgressResponse::of).toList());
    }
}

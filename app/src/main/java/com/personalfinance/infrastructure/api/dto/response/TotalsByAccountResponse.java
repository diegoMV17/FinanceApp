package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.AccountTotal;
import java.time.LocalDate;
import java.util.List;

/**
 * Los totales por cuenta del periodo, en sobre con el rango que se usó.
 *
 * <p>HU23. El rango viaja de vuelta porque cuando no se manda lo elige el
 * servidor — los últimos doce meses —, exactamente como el {@code asOf} de las
 * demás lecturas. Un gráfico sin su periodo escrito al lado es un gráfico que
 * no se puede volver a mirar mañana.
 *
 * <p>Vienen ordenados de mayor a menor, que es el orden en que se lee un
 * gráfico de categorías, y las que no se movieron vienen en cero.
 */
public record TotalsByAccountResponse(
        LocalDate from, LocalDate to, List<AccountTotalResponse> accounts) {

    public static TotalsByAccountResponse of(
            LocalDate from, LocalDate to, List<AccountTotal> totals) {

        return new TotalsByAccountResponse(
                from, to, totals.stream().map(AccountTotalResponse::of).toList());
    }
}

package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.AccountBalance;
import java.time.LocalDate;
import java.util.List;

/**
 * Los saldos, con la fecha a la que se calcularon.
 *
 * <p>No es un arreglo pelado a propósito. El {@code asOf} es opcional en la
 * petición, así que la respuesta tiene que decir con qué fecha contestó: sin
 * eso, un cliente que no manda fecha no sabe a qué día corresponden los
 * números que recibió.
 */
public record BalancesResponse(LocalDate asOf, List<BalanceResponse> accounts) {

    public static BalancesResponse of(LocalDate asOf, List<AccountBalance> balances) {
        return new BalancesResponse(
                asOf, balances.stream().map(BalanceResponse::of).toList());
    }
}

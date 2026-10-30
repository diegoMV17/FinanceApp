package com.personalfinance.infrastructure.api.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.personalfinance.application.command.MovementCommand;

/**
 * Un movimiento tal como llega por HTTP.
 *
 * <p>Un solo endpoint para los once tipos de movimiento, discriminados por el
 * campo {@code type}. El mismo DTO se anida dentro del cuerpo de una
 * corrección, que es la otra razón por la que tiene que ser una sola forma y no
 * once endpoints.
 *
 * <p>Los subtipos se declaran uno por uno. Nunca tipado por omisión: eso
 * dejaría que el cuerpo nombrara la clase a instanciar, que es como se
 * convierte un endpoint en ejecución de código ajeno.
 *
 * <p>Cada tipo sabe construir su propio comando. Ningún {@code switch} por
 * tipo de operación, igual que en el dominio: las operaciones solo se
 * diferencian en qué cuentas tocan, y eso ya lo dice cada record.
 *
 * <p>Que sea {@code sealed} lo comprueba el compilador; que la lista de
 * {@code @JsonSubTypes} diga lo mismo no lo comprueba nadie, y por eso hay un
 * test que compara las dos listas. Sin él, un movimiento nuevo compilaría
 * perfectamente y sería inalcanzable por HTTP.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OpeningBalanceRequest.class, name = "OPENING_BALANCE"),
    @JsonSubTypes.Type(value = IncomeRequest.class, name = "INCOME"),
    @JsonSubTypes.Type(value = ExpenseRequest.class, name = "EXPENSE"),
    @JsonSubTypes.Type(value = TransferRequest.class, name = "TRANSFER"),
    @JsonSubTypes.Type(value = LendRequest.class, name = "LEND"),
    @JsonSubTypes.Type(value = CollectLoanRequest.class, name = "COLLECT_LOAN"),
    @JsonSubTypes.Type(value = BorrowRequest.class, name = "BORROW"),
    @JsonSubTypes.Type(value = RepayDebtRequest.class, name = "REPAY_DEBT"),
    @JsonSubTypes.Type(value = ReceiveThirdPartyMoneyRequest.class, name = "RECEIVE_THIRD_PARTY_MONEY"),
    @JsonSubTypes.Type(value = SpendThirdPartyMoneyRequest.class, name = "SPEND_THIRD_PARTY_MONEY"),
    @JsonSubTypes.Type(value = ReturnThirdPartyMoneyRequest.class, name = "RETURN_THIRD_PARTY_MONEY")
})
public sealed interface MovementRequest permits
        OpeningBalanceRequest,
        IncomeRequest,
        ExpenseRequest,
        TransferRequest,
        LendRequest,
        CollectLoanRequest,
        BorrowRequest,
        RepayDebtRequest,
        ReceiveThirdPartyMoneyRequest,
        SpendThirdPartyMoneyRequest,
        ReturnThirdPartyMoneyRequest {

    /** El comando del caso de uso, con los tipos del dominio. */
    MovementCommand toCommand();
}

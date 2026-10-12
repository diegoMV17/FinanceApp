package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.Chart;
import com.personalfinance.domain.ledger.Operations;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * HU17. Alguien le deja plata para guardar. No es un ingreso.
 */
public record ReceiveThirdPartyMoneyCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId fund,
        AccountId heldIn,
        Money amount) implements MovementCommand {

    public ReceiveThirdPartyMoneyCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(fund, "fund");
        Objects.requireNonNull(heldIn, "heldIn");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.receiveThirdPartyMoney(
                id, date, description, chart.require(fund), chart.require(heldIn), amount);
    }
}

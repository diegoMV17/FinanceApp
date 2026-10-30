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
 * HU20. Devuelve la plata de otro.
 */
public record ReturnThirdPartyMoneyCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId fund,
        AccountId paidFrom,
        Money amount) implements MovementCommand {

    public ReturnThirdPartyMoneyCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(fund, "fund");
        Objects.requireNonNull(paidFrom, "paidFrom");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.returnThirdPartyMoney(
                id, date, description, chart.require(fund), chart.require(paidFrom), amount);
    }
}

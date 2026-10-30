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
 * HU04. Entra plata y es suya.
 */
public record IncomeCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId receivedInto,
        AccountId category,
        Money amount) implements MovementCommand {

    public IncomeCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(receivedInto, "receivedInto");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.income(
                id, date, description, chart.require(receivedInto), chart.require(category), amount);
    }
}

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
 * HU05, HU12. Sale plata y era suya.
 */
public record ExpenseCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId paidFrom,
        AccountId category,
        Money amount) implements MovementCommand {

    public ExpenseCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(paidFrom, "paidFrom");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.expense(
                id, date, description, chart.require(paidFrom), chart.require(category), amount);
    }
}

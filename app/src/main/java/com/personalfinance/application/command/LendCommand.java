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
 * HU13. Presta plata. Salió del bolsillo pero sigue siendo suya.
 */
public record LendCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId paidFrom,
        AccountId borrower,
        Money amount) implements MovementCommand {

    public LendCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(paidFrom, "paidFrom");
        Objects.requireNonNull(borrower, "borrower");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.lend(
                id, date, description, chart.require(paidFrom), chart.require(borrower), amount);
    }
}

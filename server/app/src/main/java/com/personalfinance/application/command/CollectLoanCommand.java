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
 * HU14. Le pagan, completo o en parte.
 */
public record CollectLoanCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId borrower,
        AccountId receivedInto,
        Money amount) implements MovementCommand {

    public CollectLoanCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(borrower, "borrower");
        Objects.requireNonNull(receivedInto, "receivedInto");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.collectLoan(
                id, date, description, chart.require(borrower), chart.require(receivedInto), amount);
    }
}

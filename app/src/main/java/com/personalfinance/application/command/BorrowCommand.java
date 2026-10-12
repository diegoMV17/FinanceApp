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
 * HU15. Le prestan. La plata está en su bolsillo y no es suya.
 */
public record BorrowCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId lender,
        AccountId receivedInto,
        Money amount) implements MovementCommand {

    public BorrowCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(lender, "lender");
        Objects.requireNonNull(receivedInto, "receivedInto");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.borrow(
                id, date, description, chart.require(lender), chart.require(receivedInto), amount);
    }
}

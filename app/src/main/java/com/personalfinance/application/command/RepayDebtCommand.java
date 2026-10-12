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
 * HU16. Abona a lo que debe.
 */
public record RepayDebtCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId paidFrom,
        AccountId lender,
        Money amount) implements MovementCommand {

    public RepayDebtCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(paidFrom, "paidFrom");
        Objects.requireNonNull(lender, "lender");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.repayDebt(
                id, date, description, chart.require(paidFrom), chart.require(lender), amount);
    }
}

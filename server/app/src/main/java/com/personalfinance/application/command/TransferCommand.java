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
 * HU10. Su propia plata cambia de bolsillo: ni ingreso ni gasto.
 */
public record TransferCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId from,
        AccountId to,
        Money amount) implements MovementCommand {

    public TransferCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.transfer(
                id, date, description, chart.require(from), chart.require(to), amount);
    }
}

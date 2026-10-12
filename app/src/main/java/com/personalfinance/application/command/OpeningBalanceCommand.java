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
 * HU01. El saldo con el que una cuenta empieza a existir en el libro, dicho
 * como lo lee la persona. La descripción la pone la operación.
 */
public record OpeningBalanceCommand(
        TransactionId id,
        LocalDate date,
        AccountId account,
        AccountId equity,
        Money amount) implements MovementCommand {

    public OpeningBalanceCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(equity, "equity");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.openingBalance(
                id, date, chart.require(account), chart.require(equity), amount);
    }
}

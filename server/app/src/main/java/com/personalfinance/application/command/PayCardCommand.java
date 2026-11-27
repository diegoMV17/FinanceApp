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
 * 8a-bis. Paga la tarjeta desde una cuenta de plata. No es un gasto: el gasto
 * ya ocurrió el día que se pasó la tarjeta.
 */
public record PayCardCommand(
        TransactionId id,
        LocalDate date,
        String description,
        AccountId from,
        AccountId card,
        Money amount) implements MovementCommand {

    public PayCardCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(amount, "amount");
    }

    @Override
    public Transaction toTransaction(Chart chart) {
        return Operations.payCard(
                id, date, description, chart.require(from), chart.require(card), amount);
    }
}

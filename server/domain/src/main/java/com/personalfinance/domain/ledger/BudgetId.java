package com.personalfinance.domain.ledger;

import java.util.Objects;
import java.util.UUID;

public record BudgetId(UUID value) {

    public BudgetId {
        Objects.requireNonNull(value, "value");
    }

    public static BudgetId generate() {
        return new BudgetId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

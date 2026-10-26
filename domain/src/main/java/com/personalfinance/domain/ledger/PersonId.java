package com.personalfinance.domain.ledger;

import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) {

    public PersonId {
        Objects.requireNonNull(value, "value");
    }

    public static PersonId generate() {
        return new PersonId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

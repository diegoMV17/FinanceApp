package com.personalfinance.application.port;

/** Sobre qué se anotó el cambio. La columna {@code entity} guarda el nombre. */
public enum AuditedEntity {

    TRANSACTION("transaction"),
    ACCOUNT("account");

    private final String storedAs;

    AuditedEntity(String storedAs) {
        this.storedAs = storedAs;
    }

    public String storedAs() {
        return storedAs;
    }
}

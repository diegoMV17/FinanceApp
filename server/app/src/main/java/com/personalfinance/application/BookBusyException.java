package com.personalfinance.application;

import java.time.Duration;

/**
 * La espera por el turno de escritura se venció.
 *
 * <p>No es un error del dominio — el libro está perfectamente bien — así que no
 * hereda de {@code LedgerException}: es una condición de esta capa. Nada se
 * escribió, y volver a intentarlo es seguro: un reintento idéntico se reconoce
 * como tal y no duplica nada.
 *
 * <p>La API la traducirá a un 503 con {@code Retry-After}.
 */
public final class BookBusyException extends RuntimeException {

    private final Duration waited;

    public BookBusyException(Duration waited) {
        super("El libro está ocupado; se esperó %d ms sin poder escribir"
                .formatted(waited.toMillis()));
        this.waited = waited;
    }

    public Duration waited() {
        return waited;
    }
}

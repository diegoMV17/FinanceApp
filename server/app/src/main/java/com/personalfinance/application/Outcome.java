package com.personalfinance.application;

import java.util.Objects;

/**
 * Lo que quedó en el libro, y si este intento fue el que lo puso ahí.
 *
 * <p>La distinción existe porque los ids los genera el cliente: el teléfono los
 * crea sin red y reintenta cuando se corta. Un reintento idéntico no es un
 * error — ya está aplicado, no se escribe nada y se devuelve lo que había —,
 * mientras que el mismo id con otro contenido sí lo es, y entonces esto no se
 * devuelve: se lanza la excepción del dominio.
 *
 * <p>La API traducirá {@code APPLIED} a 201 y {@code ALREADY_APPLIED} a 200 con
 * el mismo cuerpo, pero eso ya es decisión de esa capa: aquí no hay nada de
 * HTTP.
 */
public record Outcome<T>(T value, Status status) {

    public enum Status {

        /** Este intento fue el que escribió. */
        APPLIED,

        /** Ya estaba, idéntico. No se escribió nada, tampoco auditoría. */
        ALREADY_APPLIED
    }

    public Outcome {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(status, "status");
    }

    public static <T> Outcome<T> applied(T value) {
        return new Outcome<>(value, Status.APPLIED);
    }

    public static <T> Outcome<T> alreadyApplied(T value) {
        return new Outcome<>(value, Status.ALREADY_APPLIED);
    }

    public boolean wasApplied() {
        return status == Status.APPLIED;
    }
}

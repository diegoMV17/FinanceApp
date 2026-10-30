package com.personalfinance.application.port;

/**
 * Qué se hizo. Son los mismos seis valores que admite el CHECK
 * {@code audit_known_action}, y la tabla los guarda por su nombre. Eran cinco
 * en la V1; la V6 agregó {@link #LINK}.
 */
public enum AuditAction {
    CREATE,
    AMEND,
    CANCEL,
    ARCHIVE,
    RENAME,

    /**
     * Se dijo de quién es una cuenta. No es ninguna de las otras cinco — no se
     * creó nada, no se corrigió, no se anuló y no se archivó — y es justo la
     * que hay que poder auditar: mover el vínculo cambia de quién es una deuda,
     * y la tabla {@code accounts} solo conserva la persona de ahora.
     */
    LINK
}

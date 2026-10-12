package com.personalfinance.application.port;

/**
 * Qué se hizo. Son los mismos cinco valores que admite el CHECK
 * {@code audit_known_action} de la V1, y la tabla los guarda por su nombre.
 */
public enum AuditAction {
    CREATE,
    AMEND,
    CANCEL,
    ARCHIVE,
    RENAME
}

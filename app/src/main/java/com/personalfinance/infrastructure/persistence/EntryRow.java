package com.personalfinance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Una fila de {@code entries}: esta cuenta se movió estos centavos.
 *
 * <p>Solo INSERT. Los triggers de la V1 rechazan el UPDATE y el DELETE, así que
 * esta clase no tiene un solo mutador y es {@link Immutable}: lo que el esquema
 * prohíbe, el mapeo no debería ni poder intentar.
 *
 * <p>El apunte no tiene identidad en el dominio — es un valor dentro de la
 * transacción — pero la tabla necesita una clave primaria. El id lo genera
 * {@link LedgerMapper} al guardar, y al cargar no se usa para nada.
 *
 * <p>{@code position} conserva el orden en que se escribieron los apuntes. Sin
 * él, "efectivo −35.000 / alimentación +35.000" podría volver al revés: el
 * saldo sería el mismo y el movimiento se leería distinto. Desde la V3 además
 * es única dentro de su transacción, así que nadie puede colgarle a una
 * transacción ya guardada una segunda copia de sus apuntes.
 */
@Entity
@Immutable
@Table(name = "entries")
class EntryRow extends AssignedIdRow {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    // position es palabra reservada del estándar SQL; entrecomillada no depende
    // de cómo la trate cada motor.
    @Column(name = "\"position\"", nullable = false)
    private short position;

    /** Lo exige JPA. */
    protected EntryRow() {
    }

    EntryRow(UUID id, UUID transactionId, UUID accountId, long amountCents, short position) {
        this.id = id;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amountCents = amountCents;
        this.position = position;
    }

    @Override
    public UUID getId() {
        return id;
    }

    UUID transactionId() {
        return transactionId;
    }

    UUID accountId() {
        return accountId;
    }

    long amountCents() {
        return amountCents;
    }

    short position() {
        return position;
    }
}

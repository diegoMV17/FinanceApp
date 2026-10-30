package com.personalfinance.infrastructure.persistence;

import com.personalfinance.domain.ledger.TransactionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Una fila de {@code transactions}: el encabezado, sin un solo monto.
 *
 * <p>Los apuntes viven en {@link EntryRow} y se escriben junto con esta fila,
 * en un solo commit, porque el trigger que exige que sumen cero es diferido y
 * corre al cerrar la transacción de base de datos.
 *
 * <p>{@code occurred_on} es cuándo pasó; {@code recorded_at} cuándo se capturó,
 * y lo pone la base. El domingo se registra el gasto del viernes.
 *
 * <p>El encabezado es {@code updatable = false}: lo único que le pasa a una
 * transacción guardada es que la anulen. Hibernate no incluye esas columnas en
 * ningún UPDATE, y el trigger de la V3 rechaza a quien lo intente por fuera.
 */
@Entity
@Table(name = "transactions")
class TransactionRow extends AssignedIdRow {

    @Id
    private UUID id;

    @Column(name = "occurred_on", nullable = false, updatable = false)
    private LocalDate occurredOn;

    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source", columnDefinition = "transaction_source",
            nullable = false, updatable = false)
    private TransactionSource source;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_note")
    private String cancellationNote;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "cancellation_source", columnDefinition = "transaction_source")
    private TransactionSource cancellationSource;

    @Column(name = "replaces_id", updatable = false)
    private UUID replacesId;

    /**
     * Cuándo se guardó. La base la pone y nadie la reescribe: de ahí
     * {@code insertable = false}, que también es lo que hace que el orden de
     * carga sea el orden real de registro.
     */
    @Column(name = "recorded_at", insertable = false, updatable = false)
    private Instant recordedAt;

    /** Lo exige JPA. */
    protected TransactionRow() {
    }

    TransactionRow(
            UUID id,
            LocalDate occurredOn,
            String description,
            TransactionSource source,
            UUID replacesId) {

        this.id = id;
        this.occurredOn = occurredOn;
        this.description = description;
        this.source = source;
        this.replacesId = replacesId;
    }

    @Override
    public UUID getId() {
        return id;
    }

    LocalDate occurredOn() {
        return occurredOn;
    }

    String description() {
        return description;
    }

    TransactionSource source() {
        return source;
    }

    Instant cancelledAt() {
        return cancelledAt;
    }

    boolean isCancelled() {
        return cancelledAt != null;
    }

    String cancellationNote() {
        return cancellationNote;
    }

    TransactionSource cancellationSource() {
        return cancellationSource;
    }

    UUID replacesId() {
        return replacesId;
    }

    /**
     * Cuándo, por qué y de dónde salió la anulación. Los tres van juntos: el
     * CHECK de la V2 rechaza la fecha sin el origen, y el trigger de la V3
     * rechaza tocar una anulación que ya existe.
     */
    void cancel(Instant at, String note, TransactionSource from) {
        this.cancelledAt = at;
        this.cancellationNote = note;
        this.cancellationSource = from;
    }
}

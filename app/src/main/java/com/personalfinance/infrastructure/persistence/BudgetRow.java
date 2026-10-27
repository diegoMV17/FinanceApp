package com.personalfinance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una fila de {@code budgets}, y nada más que eso.
 *
 * <p>No es el presupuesto: es cómo se ve un presupuesto guardado.
 * {@code Budget} no tiene constructor vacío, exige una categoría de gasto y no
 * se deja reemplazar dos veces, así que ponerle {@code @Entity} encima dejaría
 * a JPA construir presupuestos que el dominio nunca habría permitido.
 *
 * <p>El mes se guarda como el día 1 de ese mes. Un {@code DATE} se compara, se
 * ordena y se indexa; un texto {@code "2026-03"} no hace ninguna de las tres, y
 * la V7 lo exige con un CHECK.
 *
 * <p>No hay columna con quién lo reemplazó: ese vínculo lo escribe el reemplazo
 * en {@code replaces_id}, y guardarlo en las dos direcciones serían dos
 * verdades. Lo único que la fila reemplazada guarda es cuándo dejó de contar.
 *
 * <p>Las columnas inmutables llevan {@code updatable = false}: lo único que
 * cambia en un presupuesto después de escrito es {@code superseded_at}, y los
 * triggers de la V7 son la red de atrás.
 */
@Entity
@Table(name = "budgets")
class BudgetRow extends AssignedIdRow {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "period", nullable = false, updatable = false)
    private LocalDate period;

    @Column(name = "limit_cents", nullable = false, updatable = false)
    private long limitCents;

    @Column(name = "replaces_id", updatable = false)
    private UUID replacesId;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    /** La pone la base. Solo se lee, y sirve para cargar en el orden de llegada. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Lo exige JPA. */
    protected BudgetRow() {
    }

    BudgetRow(
            UUID id,
            UUID categoryId,
            LocalDate period,
            long limitCents,
            UUID replacesId,
            Instant supersededAt) {

        this.id = id;
        this.categoryId = categoryId;
        this.period = period;
        this.limitCents = limitCents;
        this.replacesId = replacesId;
        this.supersededAt = supersededAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    UUID categoryId() {
        return categoryId;
    }

    LocalDate period() {
        return period;
    }

    long limitCents() {
        return limitCents;
    }

    UUID replacesId() {
        return replacesId;
    }

    Instant supersededAt() {
        return supersededAt;
    }

    boolean isSuperseded() {
        return supersededAt != null;
    }

    /** Deja de contar. El trigger de la V7 impide que se deshaga. */
    void supersede(Instant at) {
        this.supersededAt = at;
    }
}

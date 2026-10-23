package com.personalfinance.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Una fila de {@code people}, y nada más que eso.
 *
 * <p>No es la persona: es cómo se ve una persona guardada. {@code Person} no
 * tiene constructor vacío y archivarla dos veces falla, así que ponerle
 * {@code @Entity} encima dejaría a JPA construir personas que el dominio nunca
 * habría permitido. De ahí esta clase, que a cambio no sabe ninguna regla.
 *
 * <p>No hay columna con las cuentas de la persona, y no es un olvido: el
 * vínculo vive en {@code accounts.person_id}, y guardarlo también aquí serían
 * dos verdades que algún día no coinciden.
 *
 * <p>{@code created_at} y {@code updated_at} los pone la base. Dejar que los
 * fije Java sería una segunda fuente de verdad para la misma fecha.
 */
@Entity
@Table(name = "people")
class PersonRow extends AssignedIdRow {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** La pone la base. Solo se lee, y sirve para cargar en el orden de llegada. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Lo exige JPA. */
    protected PersonRow() {
    }

    PersonRow(UUID id, String name, Instant archivedAt) {
        this.id = id;
        this.name = name;
        this.archivedAt = archivedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    String name() {
        return name;
    }

    Instant archivedAt() {
        return archivedAt;
    }

    boolean isArchived() {
        return archivedAt != null;
    }

    void renameTo(String newName) {
        this.name = newName;
    }

    void archivedAt(Instant when) {
        this.archivedAt = when;
    }
}

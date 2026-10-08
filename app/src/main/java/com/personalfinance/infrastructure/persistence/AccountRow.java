package com.personalfinance.infrastructure.persistence;

import com.personalfinance.domain.ledger.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Una fila de {@code accounts}, y nada más que eso.
 *
 * <p>No es la cuenta: es cómo se ve una cuenta guardada. {@code Account} no
 * tiene constructor vacío y archivarla exige saldo cero, así que ponerle
 * {@code @Entity} encima dejaría a JPA construir cuentas que el dominio nunca
 * habría permitido. De ahí esta clase, que a cambio no sabe ninguna regla.
 *
 * <p>El tipo es {@code updatable = false}: toda la historia de la cuenta se lee
 * según su tipo, y cambiarlo convertiría de un golpe un banco en una categoría
 * de gasto. El trigger de la V3 lo rechaza también por fuera de la aplicación.
 *
 * <p>{@code created_at} y {@code updated_at} los pone la base de datos. Dejar
 * que los fije Java sería una segunda fuente de verdad para la misma fecha.
 */
@Entity
@Table(name = "accounts")
class AccountRow extends AssignedIdRow {

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", columnDefinition = "account_type",
            nullable = false, updatable = false)
    private AccountType type;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** La pone la base. Solo se lee, y sirve para cargar en el orden de apertura. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Lo exige JPA. */
    protected AccountRow() {
    }

    AccountRow(UUID id, AccountType type, String name, Instant archivedAt) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.archivedAt = archivedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    AccountType type() {
        return type;
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

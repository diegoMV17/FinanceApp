package com.personalfinance.infrastructure.persistence;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Lo que comparten las filas cuyo id lo pone el dominio y no la base.
 *
 * <p>Spring Data decide entre INSERT y UPDATE mirando el id: si no es nulo,
 * asume que la fila ya existe y hace {@code merge}. Aquí el id nunca es nulo —
 * lo trae el dominio —, así que sin esta clase guardar dos veces la misma
 * transacción no fallaría: reescribiría el encabezado y le volvería a insertar
 * los apuntes. El trigger de balance no lo notaría, porque cero más cero sigue
 * siendo cero, y el saldo quedaría duplicado en silencio. Con la sincronización
 * del teléfono, un reintento después de un corte de red es exactamente ese caso.
 *
 * <p>Con {@link Persistable}, una fila recién construida siempre es nueva y se
 * inserta; si el id ya existe, la clave primaria la rechaza en voz alta. Solo
 * una fila que vino de la base ({@code @PostLoad}) o que ya se guardó
 * ({@code @PostPersist}) puede llegar a actualizarse.
 */
@MappedSuperclass
abstract class AssignedIdRow implements Persistable<UUID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        isNew = false;
    }
}

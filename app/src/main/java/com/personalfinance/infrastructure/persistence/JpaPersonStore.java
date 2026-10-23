package com.personalfinance.infrastructure.persistence;

import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonAlreadyArchivedException;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.UnknownPersonException;
import com.personalfinance.domain.ledger.port.PersonStore;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * El directorio de personas, contra Postgres.
 *
 * <p>No hay {@code delete}: las personas se archivan. Lo que cambia después de
 * escribir una persona es su nombre y su fecha de archivado, y nada más — por
 * eso esta clase solo sabe hacer esas dos cosas.
 *
 * <p>Escribir dos veces la misma persona falla con la clave primaria (ver
 * {@link AssignedIdRow}) en vez de reescribirla: sin eso, "agregar" una persona
 * que ya existía podía cambiarle el nombre o desarchivarla sin dejar rastro.
 */
@Repository
class JpaPersonStore implements PersonStore {

    private final PersonRowRepository people;

    JpaPersonStore(PersonRowRepository people) {
        this.people = people;
    }

    @Override
    @Transactional
    public void add(Person person) {
        people.saveAndFlush(LedgerMapper.rowOf(person));
    }

    @Override
    @Transactional
    public void rename(PersonId id, String newName) {
        require(id).renameTo(newName);
    }

    @Override
    @Transactional
    public void markArchived(PersonId id, Instant at) {
        PersonRow row = require(id);
        if (row.isArchived()) {
            throw new PersonAlreadyArchivedException(LedgerMapper.personFrom(row));
        }
        row.archivedAt(at);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Person> all() {
        return people.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(LedgerMapper::personFrom)
                .toList();
    }

    /** Devuelve la fila gestionada: cambiarla ya es el UPDATE, al cerrar. */
    private PersonRow require(PersonId id) {
        return people.findById(id.value())
                .orElseThrow(() -> new UnknownPersonException(id));
    }
}

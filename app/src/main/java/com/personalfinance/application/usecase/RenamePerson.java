package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.RenamePersonCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Directory;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.port.PersonStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambiarle el nombre a una persona.
 *
 * <p>Como con una cuenta, la bitácora guarda aquí el nombre anterior: la tabla
 * {@code people} solo conserva el de ahora.
 *
 * <p>Pedir el nombre que ya tiene, exacto salvo los espacios de los extremos,
 * es un reintento y no escribe nada. Exacto quiere decir con sus mayúsculas: la
 * regla con que el directorio compara nombres las ignora porque sirve para otra
 * cosa — decidir si dos personas serían indistinguibles en una lista — y usarla
 * aquí haría que arreglar «juan» a «Juan» respondiera «ya aplicado» sin cambiar
 * nada.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class RenamePerson {

    private final BookLock lock;
    private final BookLoader loader;
    private final PersonStore people;
    private final AuditTrail audit;

    RenamePerson(BookLock lock, BookLoader loader, PersonStore people, AuditTrail audit) {
        this.lock = lock;
        this.loader = loader;
        this.people = people;
        this.audit = audit;
    }

    /**
     * @return la persona con el nombre que quedó
     * @throws com.personalfinance.domain.ledger.DuplicatePersonNameException
     *         si ese nombre ya lo tiene alguien que se puede elegir
     * @throws com.personalfinance.domain.ledger.UnknownPersonException
     *         si el directorio no tiene esa persona
     */
    @Transactional
    public Outcome<Person> execute(RenamePersonCommand command) {
        lock.acquire();
        Directory directory = loader.load().directory();

        Person person = directory.require(command.id());
        if (person.isNamed(command.newName())) {
            return Outcome.alreadyApplied(person);
        }

        String before = person.name();
        directory.rename(command.id(), command.newName());

        // El nombre que guarda la base es el que quedó en el dominio, ya
        // recortado, no el que llegó en el comando.
        people.rename(command.id(), person.name());
        audit.record(AuditRecord.personRenamed(command.id(), before, person.name()));

        return Outcome.applied(person);
    }
}

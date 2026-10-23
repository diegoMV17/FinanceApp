package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.OpenPersonCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Directory;
import com.personalfinance.domain.ledger.DuplicatePersonException;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.port.PersonStore;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU19. Escribir a alguien en el directorio.
 *
 * <p>El mismo id con el mismo contenido es un reintento. El mismo id con otro
 * contenido es una contradicción, y otro id con un nombre que ya tiene alguien
 * que se puede elegir lo rechaza el dominio: elegir «Juan» de una lista que lo
 * tiene dos veces es una moneda al aire, y la mitad de las veces la deuda cae
 * sobre el Juan equivocado.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class OpenPerson {

    private final BookLock lock;
    private final BookLoader loader;
    private final PersonStore people;
    private final AuditTrail audit;

    OpenPerson(BookLock lock, BookLoader loader, PersonStore people, AuditTrail audit) {
        this.lock = lock;
        this.loader = loader;
        this.people = people;
        this.audit = audit;
    }

    /**
     * @return la persona que quedó en el directorio
     * @throws com.personalfinance.domain.ledger.DuplicatePersonException
     *         si ese id ya está en el directorio con otro contenido
     * @throws com.personalfinance.domain.ledger.DuplicatePersonNameException
     *         si ya hay alguien abierto con ese nombre
     */
    @Transactional
    public Outcome<Person> execute(OpenPersonCommand command) {
        lock.acquire();
        Directory directory = loader.load().directory();

        Optional<Person> stored = directory.find(command.id());
        if (stored.isPresent()) {
            return sameOrConflict(stored.get(), command);
        }

        Person opened = directory.open(command.id(), command.name());
        people.open(opened);
        audit.record(AuditRecord.personOpened(opened.id()));

        return Outcome.applied(opened);
    }

    /**
     * La persona que se pidió se construye solo para compararla: es la forma de
     * que el nombre llegue recortado y con los mismos límites que tendría si se
     * fuera a guardar, y la comparación use la misma regla de nombres que el
     * directorio.
     */
    private static Outcome<Person> sameOrConflict(Person stored, OpenPersonCommand command) {
        Person asked = Person.open(command.id(), command.name());
        if (stored.sameContentAs(asked)) {
            return Outcome.alreadyApplied(stored);
        }
        throw new DuplicatePersonException(command.id());
    }
}

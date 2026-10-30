package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.ArchivePersonCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.port.PersonStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archivar a una persona.
 *
 * <p>Es el otro caso que explica para qué está el candado. Archivar exige que
 * cada cuenta de esa persona esté archivada, y esa regla cruza filas: sin
 * turno, una petición que le vincula una cuenta nueva a Juan mientras esta
 * decide deja a Juan archivado con una deuda viva colgando, y archivar no se
 * deshace.
 *
 * <p>Una persona ya archivada responde «ya estaba» con su fecha original: un
 * archivado no tiene más contenido que comparar.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class ArchivePerson {

    private final BookLock lock;
    private final BookLoader loader;
    private final PersonStore people;
    private final AuditTrail audit;
    private final Clock clock;

    ArchivePerson(
            BookLock lock,
            BookLoader loader,
            PersonStore people,
            AuditTrail audit,
            Clock clock) {

        this.lock = lock;
        this.loader = loader;
        this.people = people;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * @return la persona archivada, con la fecha en que lo fue
     * @throws com.personalfinance.domain.ledger.PersonStillHasOpenAccountsException
     *         si todavía tiene cuentas abiertas
     * @throws com.personalfinance.domain.ledger.UnknownPersonException
     *         si el directorio no tiene esa persona
     */
    @Transactional
    public Outcome<Person> execute(ArchivePersonCommand command) {
        lock.acquire();
        Book book = loader.load();

        Person person = book.directory().require(command.id());
        if (person.isArchived()) {
            return Outcome.alreadyApplied(person);
        }

        // La misma fecha en el libro y en la base. Dos llamadas al reloj darían
        // dos fechas distintas para el mismo hecho, y el archivado no se
        // reescribe después.
        Instant when = clock.instant();
        book.directory().archive(command.id(), book.chart(), when);
        people.markArchived(command.id(), when);
        audit.record(AuditRecord.personArchived(command.id()));

        return Outcome.applied(person);
    }
}

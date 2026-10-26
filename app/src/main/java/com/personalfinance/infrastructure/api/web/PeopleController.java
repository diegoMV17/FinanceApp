package com.personalfinance.infrastructure.api.web;

import com.personalfinance.application.command.ArchivePersonCommand;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.ArchivePerson;
import com.personalfinance.application.usecase.LinkAccountToPerson;
import com.personalfinance.application.usecase.OpenPerson;
import com.personalfinance.application.usecase.RenamePerson;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.infrastructure.api.dto.request.LinkAccountRequest;
import com.personalfinance.infrastructure.api.dto.request.OpenPersonRequest;
import com.personalfinance.infrastructure.api.dto.request.RenamePersonRequest;
import com.personalfinance.infrastructure.api.dto.response.AccountResponse;
import com.personalfinance.infrastructure.api.dto.response.PeopleResponse;
import com.personalfinance.infrastructure.api.dto.response.PersonDetailResponse;
import com.personalfinance.infrastructure.api.dto.response.PersonResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU19, HU21. Las personas: escribirlas, renombrarlas, archivarlas, decir qué
 * cuentas son suyas, y leer cómo está la cuenta con cada una.
 *
 * <p>Un controlador no hace nada más que cuatro cosas en este orden: validar lo
 * que llegó, traducirlo a un comando, llamar al caso de uso y traducir lo que
 * volvió. Ninguna regla, ninguna transacción, ningún candado.
 *
 * <p>Vincular es un sub-recurso en plural de la persona —
 * {@code POST /api/people/{id}/accounts} — y no un {@code PATCH} sobre la
 * cuenta. Lo que se manda es el hecho «esta cuenta pasó a ser de Pedro», que es
 * también lo que hace que mover el vínculo se exprese sin inventar nada: la
 * misma petición, a la otra persona.
 *
 * <p>El {@code asOf} de las dos lecturas sale del mismo reloj que usan los casos
 * de uso cuando no viene: ver {@link BookController}.
 */
@RestController
@RequestMapping(path = "/api/people", produces = MediaType.APPLICATION_JSON_VALUE)
class PeopleController {

    private final OpenPerson openPerson;
    private final RenamePerson renamePerson;
    private final ArchivePerson archivePerson;
    private final LinkAccountToPerson linkAccountToPerson;
    private final ReadBook book;
    private final Clock clock;

    PeopleController(
            OpenPerson openPerson,
            RenamePerson renamePerson,
            ArchivePerson archivePerson,
            LinkAccountToPerson linkAccountToPerson,
            ReadBook book,
            Clock clock) {

        this.openPerson = openPerson;
        this.renamePerson = renamePerson;
        this.archivePerson = archivePerson;
        this.linkAccountToPerson = linkAccountToPerson;
        this.book = book;
        this.clock = clock;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PersonResponse> open(@Valid @RequestBody OpenPersonRequest request) {
        var opened = openPerson.execute(request.toCommand());
        return Answers.of(opened, PersonResponse.of(opened.value()));
    }

    @PostMapping(path = "/{id}/renames", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PersonResponse> rename(
            @PathVariable UUID id, @Valid @RequestBody RenamePersonRequest request) {

        var renamed = renamePerson.execute(request.toCommand(id));
        return Answers.of(renamed, PersonResponse.of(renamed.value()));
    }

    /**
     * Archivar no lleva cuerpo, así que no declara {@code consumes}: no hay nada
     * que un cliente pueda decir aquí.
     */
    @PostMapping("/{id}/archivals")
    ResponseEntity<PersonResponse> archive(@PathVariable UUID id) {
        var archived = archivePerson.execute(new ArchivePersonCommand(new PersonId(id)));
        return Answers.of(archived, PersonResponse.of(archived.value()));
    }

    /**
     * Responde con la cuenta y no con la persona: la cuenta es la que cambió, y
     * su respuesta ya dice de quién es sin tener que recorrer el libro entero
     * para armar una posición que el cliente no pidió.
     */
    @PostMapping(path = "/{id}/accounts", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AccountResponse> link(
            @PathVariable UUID id, @Valid @RequestBody LinkAccountRequest request) {

        var linked = linkAccountToPerson.execute(request.toCommand(id));
        return Answers.of(linked, AccountResponse.of(linked.value()));
    }

    @GetMapping
    PeopleResponse people(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {

        LocalDate when = Dates.orToday(asOf, clock);
        return PeopleResponse.of(when, book.people(when));
    }

    @GetMapping("/{id}")
    PersonDetailResponse person(
            @PathVariable UUID id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {

        LocalDate when = Dates.orToday(asOf, clock);
        return PersonDetailResponse.of(when, book.person(new PersonId(id), when));
    }

}

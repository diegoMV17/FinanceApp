package com.personalfinance.infrastructure.api.web;

import com.personalfinance.application.command.ArchiveAccountCommand;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.ArchiveAccount;
import com.personalfinance.application.usecase.OpenAccount;
import com.personalfinance.application.usecase.RenameAccount;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.infrastructure.api.dto.request.OpenAccountRequest;
import com.personalfinance.infrastructure.api.dto.request.RenameAccountRequest;
import com.personalfinance.infrastructure.api.dto.response.AccountResponse;
import com.personalfinance.infrastructure.api.dto.response.MovementsResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las cuentas: abrirlas, renombrarlas, archivarlas y ver sus movimientos.
 *
 * <p>Un controlador no hace nada más que cuatro cosas en este orden: validar lo
 * que llegó, traducirlo a un comando, llamar al caso de uso y traducir lo que
 * volvió. Ninguna regla, ninguna transacción, ningún candado: todo eso vive en
 * los casos de uso, que son los que saben tomar el turno y escribir en una sola
 * transacción.
 *
 * <p>Los cambios son sub-recursos en plural — {@code /renames},
 * {@code /archivals} — y no un {@code PATCH} sobre la cuenta. Lo que se manda
 * es un hecho que ocurrió, no un campo que se sobreescribe, y así el 201 dice
 * algo cierto: se creó un renombrado. Es también lo que hace natural que el
 * libro no borre nada.
 */
@RestController
@RequestMapping(path = "/api/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
class AccountsController {

    private final OpenAccount openAccount;
    private final RenameAccount renameAccount;
    private final ArchiveAccount archiveAccount;
    private final ReadBook book;

    AccountsController(
            OpenAccount openAccount,
            RenameAccount renameAccount,
            ArchiveAccount archiveAccount,
            ReadBook book) {

        this.openAccount = openAccount;
        this.renameAccount = renameAccount;
        this.archiveAccount = archiveAccount;
        this.book = book;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        var opened = openAccount.execute(request.toCommand());
        return Answers.of(opened, AccountResponse.of(opened.value()));
    }

    @PostMapping(path = "/{id}/renames", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AccountResponse> rename(
            @PathVariable UUID id, @Valid @RequestBody RenameAccountRequest request) {

        var renamed = renameAccount.execute(request.toCommand(id));
        return Answers.of(renamed, AccountResponse.of(renamed.value()));
    }

    /**
     * Archivar no lleva cuerpo, así que no declara {@code consumes}: no hay
     * nada que un cliente pueda decir aquí, y exigirle un JSON vacío sería
     * pedirle que escriba algo sin contenido.
     */
    @PostMapping("/{id}/archivals")
    ResponseEntity<AccountResponse> archive(@PathVariable UUID id) {
        var archived = archiveAccount.execute(new ArchiveAccountCommand(new AccountId(id)));
        return Answers.of(archived, AccountResponse.of(archived.value()));
    }

    @GetMapping("/{id}/movements")
    MovementsResponse movements(@PathVariable UUID id) {
        return MovementsResponse.of(id, book.movementsOf(new AccountId(id)));
    }
}

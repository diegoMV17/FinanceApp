package com.personalfinance.infrastructure.api.web;

import com.personalfinance.application.read.MovementFilter;
import com.personalfinance.application.read.PageRequest;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.AmendTransaction;
import com.personalfinance.application.usecase.CancelTransaction;
import com.personalfinance.application.usecase.RecordMovement;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.Entry;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import com.personalfinance.infrastructure.api.dto.request.AmendMovementRequest;
import com.personalfinance.infrastructure.api.dto.request.CancelMovementRequest;
import com.personalfinance.infrastructure.api.dto.request.MovementRequest;
import com.personalfinance.infrastructure.api.dto.response.CancellationResponse;
import com.personalfinance.infrastructure.api.dto.response.HistoryResponse;
import com.personalfinance.infrastructure.api.dto.response.MovementResponse;
import com.personalfinance.infrastructure.api.dto.response.RevisionsResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
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
 * Los movimientos: registrarlos, anularlos, corregirlos y ver su historia.
 *
 * <p>Un solo endpoint para los once tipos de movimiento. El tipo lo dice el
 * campo {@code type} del cuerpo y cada variante sabe construir su propio
 * comando, así que aquí no hay ni un {@code switch}: igual que en el dominio,
 * donde las operaciones solo se diferencian en qué cuentas tocan.
 *
 * <p>Anular y corregir también son sub-recursos en plural. Una corrección no
 * reemplaza nada en su sitio: anula la original y registra un movimiento nuevo
 * que la apunta, y eso es lo que devuelve.
 */
@RestController
@RequestMapping(path = "/api/movements", produces = MediaType.APPLICATION_JSON_VALUE)
class MovementsController {

    /**
     * Cuántas cuentas admite un filtro. Cada una es una comprobación más por
     * movimiento, y nadie pregunta por diez cuentas a la vez.
     */
    private static final int MAX_ACCOUNTS_PER_QUERY = 10;

    private final RecordMovement recordMovement;
    private final CancelTransaction cancelTransaction;
    private final AmendTransaction amendTransaction;
    private final ReadBook book;

    MovementsController(
            RecordMovement recordMovement,
            CancelTransaction cancelTransaction,
            AmendTransaction amendTransaction,
            ReadBook book) {

        this.recordMovement = recordMovement;
        this.cancelTransaction = cancelTransaction;
        this.amendTransaction = amendTransaction;
        this.book = book;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MovementResponse> record(@Valid @RequestBody MovementRequest request) {
        var recorded = recordMovement.execute(request.toCommand());
        return Answers.of(recorded, MovementResponse.of(recorded.value()));
    }

    /**
     * El id del movimiento sale de la ruta y se devuelve en el cuerpo, porque
     * {@code Cancellation} no lo trae: en el dominio una anulación vive dentro
     * de su transacción y no necesita nombrarla.
     */
    @PostMapping(path = "/{id}/cancellations", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CancellationResponse> cancel(
            @PathVariable UUID id, @Valid @RequestBody CancelMovementRequest request) {

        var cancelled = cancelTransaction.execute(request.toCommand(id));
        return Answers.of(cancelled, CancellationResponse.of(id, cancelled.value()));
    }

    @PostMapping(path = "/{id}/amendments", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MovementResponse> amend(
            @PathVariable UUID id, @Valid @RequestBody AmendMovementRequest request) {

        var amended = amendTransaction.execute(request.toCommand(id));
        return Answers.of(amended, MovementResponse.of(amended.value()));
    }

    @GetMapping("/{id}/revisions")
    RevisionsResponse revisions(@PathVariable UUID id) {
        return RevisionsResponse.of(id, book.revisionsOf(new TransactionId(id)));
    }

    /**
     * HU24, HU25. El historial general, con los siete filtros y por páginas.
     *
     * <p>Los parámetros van sueltos y no en un objeto enlazado con
     * {@code @ModelAttribute} por una razón de contrato: una fecha ilegible en
     * un {@code @RequestParam} llega como {@code TypeMismatchException} y
     * responde {@code invalid-path}, exactamente igual que un {@code asOf}
     * ilegible, mientras que el mismo error dentro de un objeto enlazado llega
     * como un error de enlace y contestaría otro código para la misma
     * equivocación.
     *
     * <p>«Categoría» y «fondo» de HU25 no son parámetros propios: una categoría
     * es una cuenta de gasto y un fondo es una cuenta de terceros, así que
     * serían el mismo dato con tres nombres. {@code account} se puede repetir y
     * significa <strong>y</strong>, no <strong>o</strong>, que es lo que hace
     * posible «lo que gasté de Nequi en Alimentación».
     *
     * <p>El tope de cuentas por consulta es lo mismo que el tope de página: una
     * petición corta no debe poder encargar trabajo sin límite.
     */
    @GetMapping
    HistoryResponse history(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false) @Size(max = MAX_ACCOUNTS_PER_QUERY) List<UUID> account,
            @RequestParam(required = false) UUID person,
            @RequestParam(required = false)
            @Min(0) @Max(Entry.MAX_ABSOLUTE_CENTS) Long minInCents,
            @RequestParam(required = false)
            @Min(0) @Max(Entry.MAX_ABSOLUTE_CENTS) Long maxInCents,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(PageRequest.MAX_SIZE) Integer size) {

        MovementFilter filter = new MovementFilter(
                from,
                to,
                type,
                account == null ? List.of() : account.stream().map(AccountId::new).toList(),
                person == null ? null : new PersonId(person),
                centsOrNull(minInCents),
                centsOrNull(maxInCents));

        return HistoryResponse.of(book.movements(filter, PageRequest.of(page, size)));
    }

    private static Money centsOrNull(Long cents) {
        return cents == null ? null : Money.ofCents(cents);
    }
}

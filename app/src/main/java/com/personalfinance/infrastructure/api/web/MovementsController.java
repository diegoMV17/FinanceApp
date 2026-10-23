package com.personalfinance.infrastructure.api.web;

import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.AmendTransaction;
import com.personalfinance.application.usecase.CancelTransaction;
import com.personalfinance.application.usecase.RecordMovement;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.infrastructure.api.dto.request.AmendMovementRequest;
import com.personalfinance.infrastructure.api.dto.request.CancelMovementRequest;
import com.personalfinance.infrastructure.api.dto.request.MovementRequest;
import com.personalfinance.infrastructure.api.dto.response.CancellationResponse;
import com.personalfinance.infrastructure.api.dto.response.MovementResponse;
import com.personalfinance.infrastructure.api.dto.response.RevisionsResponse;
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
}

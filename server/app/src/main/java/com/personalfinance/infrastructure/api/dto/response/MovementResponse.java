package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.RevisionView;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.TransactionSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un movimiento entero, con sus apuntes.
 *
 * <p>Una sola forma para tres usos: lo que devuelve registrar un movimiento, lo
 * que devuelve una corrección, y cada versión en el historial de un movimiento.
 * Son el mismo dato, así que son el mismo JSON; dos formas para lo mismo
 * obligarían a un cliente a escribir dos lectores.
 *
 * <p>El {@code source} se devuelve pero no se acepta: de dónde salió un
 * movimiento lo decide quien lo registra, no quien lo pide. Un cliente que
 * mande {@code source} recibe un 400, porque los campos desconocidos se rechazan.
 */
public record MovementResponse(
        UUID id,
        LocalDate date,
        String description,
        TransactionSource source,
        List<EntryResponse> entries,
        Instant cancelledAt,
        String cancellationReason,
        UUID replaces) {

    public static MovementResponse of(Transaction movement) {
        return new MovementResponse(
                movement.id().value(),
                movement.date(),
                movement.description(),
                movement.source(),
                movement.entries().stream().map(EntryResponse::of).toList(),
                movement.cancellation().map(cancellation -> cancellation.at()).orElse(null),
                movement.cancellation().map(cancellation -> cancellation.reason()).orElse(null),
                movement.replaces().map(TransactionId::value).orElse(null));
    }

    public static MovementResponse of(RevisionView revision) {
        return new MovementResponse(
                revision.id().value(),
                revision.date(),
                revision.description(),
                revision.source(),
                revision.entries().stream().map(EntryResponse::of).toList(),
                revision.cancelledAt(),
                revision.cancellationReason(),
                revision.replaces() == null ? null : revision.replaces().value());
    }
}

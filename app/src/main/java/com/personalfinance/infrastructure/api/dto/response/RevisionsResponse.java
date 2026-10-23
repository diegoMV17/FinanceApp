package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.RevisionView;
import java.util.List;
import java.util.UUID;

/**
 * El historial de un movimiento: la versión original y cada corrección.
 *
 * <p>Nada se borra en este libro, así que esto es la cadena entera.
 */
public record RevisionsResponse(UUID movement, List<MovementResponse> revisions) {

    public static RevisionsResponse of(UUID movement, List<RevisionView> revisions) {
        return new RevisionsResponse(
                movement, revisions.stream().map(MovementResponse::of).toList());
    }
}

package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.MovementLine;
import com.personalfinance.application.read.Page;
import java.util.List;

/**
 * El historial general: el trozo que se contestó y cuántos movimientos cumplían
 * el filtro.
 *
 * <p>HU24, HU25. No repite los filtros con los que se preguntó, y eso es a
 * propósito. Las otras lecturas devuelven su {@code asOf} porque cuando no se
 * manda el servidor elige uno — hoy — y el cliente no tiene forma de saber
 * cuál. Aquí no hay nada que el servidor elija: un {@code from} ausente
 * significa «sin límite», y eso ya lo sabe quien no lo mandó.
 *
 * <p>El total sí hace falta, y es el de los que pasaron el filtro, no el de la
 * página: sin él nadie sabe si queda más que pedir.
 */
public record HistoryResponse(
        int page, int size, long total, List<HistoryLineResponse> movements) {

    public static HistoryResponse of(Page<MovementLine> movements) {
        return new HistoryResponse(
                movements.page(),
                movements.size(),
                movements.total(),
                movements.items().stream().map(HistoryLineResponse::of).toList());
    }
}

package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.MovementView;
import com.personalfinance.application.read.Page;
import java.util.List;
import java.util.UUID;

/**
 * El extracto de una cuenta: la cuenta que se pidió, el trozo que se contestó
 * y cuántos movimientos hay en total.
 *
 * <p>HU24. El sobre dice con qué página y qué tamaño contestó por lo mismo que
 * las demás lecturas traen su {@code asOf}: los dos parámetros son opcionales,
 * así que la respuesta tiene que decir cuáles usó. Y el total va aparte porque
 * sin él el cliente no sabe si queda más.
 */
public record MovementsResponse(
        UUID account,
        int page,
        int size,
        long total,
        List<MovementLineResponse> movements) {

    public static MovementsResponse of(UUID account, Page<MovementView> movements) {
        return new MovementsResponse(
                account,
                movements.page(),
                movements.size(),
                movements.total(),
                movements.items().stream().map(MovementLineResponse::of).toList());
    }
}

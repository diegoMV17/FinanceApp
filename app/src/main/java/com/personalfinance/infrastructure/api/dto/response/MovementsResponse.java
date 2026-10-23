package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.MovementView;
import java.util.List;
import java.util.UUID;

/** El extracto de una cuenta, con la cuenta que se pidió. */
public record MovementsResponse(UUID account, List<MovementLineResponse> movements) {

    public static MovementsResponse of(UUID account, List<MovementView> movements) {
        return new MovementsResponse(
                account, movements.stream().map(MovementLineResponse::of).toList());
    }
}

package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.PersonView;
import java.time.LocalDate;

/**
 * Una sola persona con su posición, en el mismo sobre con {@code asOf} que
 * {@link PeopleResponse}.
 *
 * <p>Un sobre y no la persona pelada, por la misma razón: la fecha es opcional
 * al preguntar, así que la respuesta tiene que decir con cuál contestó.
 */
public record PersonDetailResponse(LocalDate asOf, PersonPositionResponse person) {

    public static PersonDetailResponse of(LocalDate asOf, PersonView person) {
        return new PersonDetailResponse(asOf, PersonPositionResponse.of(person));
    }
}

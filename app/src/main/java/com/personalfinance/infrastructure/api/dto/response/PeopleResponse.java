package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.PersonView;
import java.time.LocalDate;
import java.util.List;

/**
 * Todas las personas con su posición, a la fecha con la que se contestó.
 *
 * <p>No es un arreglo pelado, por lo mismo que los saldos: el {@code asOf} es
 * opcional en la petición, así que la respuesta tiene que decir a qué día
 * corresponden los números que mandó.
 *
 * <p>Vienen también las archivadas, cada una con su fecha. Un reporte de marzo
 * tiene que seguir nombrando a quien dejó de deberle plata en abril.
 */
public record PeopleResponse(LocalDate asOf, List<PersonPositionResponse> people) {

    public static PeopleResponse of(LocalDate asOf, List<PersonView> people) {
        return new PeopleResponse(
                asOf, people.stream().map(PersonPositionResponse::of).toList());
    }
}

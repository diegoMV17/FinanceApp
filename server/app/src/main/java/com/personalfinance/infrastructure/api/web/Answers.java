package com.personalfinance.infrastructure.api.web;

import com.personalfinance.application.Outcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * El único sitio que convierte un {@link Outcome} en un código HTTP.
 *
 * <p>201 si esta petición fue la que escribió, 200 si era un reintento y lo que
 * se devuelve ya estaba. Esa es toda la pregunta que un cliente necesita
 * responder: «¿lo hice yo o ya estaba hecho?».
 *
 * <p>Está aquí, en una clase de tres líneas, para que ningún controlador pueda
 * equivocarse por su cuenta. Repartido por seis controladores, el día que uno
 * devolviera 200 donde debía 201 nadie lo notaría: el cuerpo sería idéntico y
 * el libro quedaría igual de correcto. Lo único que se rompería es la forma que
 * tiene el cliente de saber si su reintento llegó a escribir.
 *
 * <p>Que un renombrado o un archivado respondan 201 sin crear un recurso nuevo
 * es deliberado y es la lectura literal de la decisión del proyecto. La
 * alternativa — 200 para los que solo cambian estado — obligaría al cliente a
 * saber, endpoint por endpoint, qué código significa «escribí yo», y eso es
 * precisamente lo que esta uniformidad le ahorra.
 */
final class Answers {

    /**
     * @param outcome lo que dijo el caso de uso
     * @param body    la respuesta ya traducida a DTO
     */
    static <T, R> ResponseEntity<R> of(Outcome<T> outcome, R body) {
        return ResponseEntity
                .status(outcome.wasApplied() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(body);
    }

    private Answers() {
    }
}

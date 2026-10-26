package com.personalfinance.infrastructure.api.web;

import com.personalfinance.application.read.ReadBook;
import com.personalfinance.infrastructure.api.dto.response.BalancesResponse;
import com.personalfinance.infrastructure.api.dto.response.SummaryResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las dos preguntas que se le hacen al libro entero.
 *
 * <p>El {@code asOf} es opcional y, si no viene, lo pone el reloj de la
 * aplicación. El dominio sigue sin leer ningún reloj — sus consultas reciben la
 * fecha como parámetro — y es aquí, en la infraestructura, donde se decide qué
 * día es hoy. Ese reparto es a propósito: así los tests del dominio no
 * dependen del calendario.
 *
 * <p>El reloj es el bean en UTC truncado a microsegundos, el mismo que usan los
 * casos de uso. Dos relojes distintos darían dos «hoy» distintos según a quién
 * se le pregunte, y por eso «qué día es hoy» se contesta en un solo sitio:
 * {@link Dates}.
 */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
class BookController {

    private final ReadBook book;
    private final Clock clock;
    private final ZoneId timezone;

    BookController(ReadBook book, Clock clock, ZoneId timezone) {
        this.book = book;
        this.clock = clock;
        this.timezone = timezone;
    }

    @GetMapping("/summary")
    SummaryResponse summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {

        return SummaryResponse.of(book.summary(Dates.orToday(asOf, clock, timezone)));
    }

    @GetMapping("/balances")
    BalancesResponse balances(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {

        LocalDate when = Dates.orToday(asOf, clock, timezone);
        return BalancesResponse.of(when, book.balances(when));
    }
}

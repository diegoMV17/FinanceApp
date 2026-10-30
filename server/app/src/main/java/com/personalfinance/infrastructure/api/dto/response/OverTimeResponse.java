package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.BookSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * La serie de indicadores, un punto a fin de cada mes del rango.
 *
 * <p>HU23. De aquí salen tres de las cinco estadísticas de un solo viaje: la
 * evolución del saldo es {@code moneyReallyMineInCents} punto a punto, el
 * dinero prestado y el adeudado son los tipos {@code RECEIVABLE} y
 * {@code PAYABLE} de cada punto, y los ingresos contra los gastos de cada mes
 * son los dos campos del mes. Un endpoint por estadística habría devuelto tres
 * veces la misma serie recortada de tres formas distintas.
 *
 * <p>Cada punto es un {@link SummaryResponse}, el mismo cuerpo que devuelve
 * {@code GET /api/summary}: esa lectura es, literalmente, el último punto de
 * esta serie. Un cliente aprende una forma y no dos.
 *
 * <p><strong>El rango elige los puntos, no lo que se cuenta.</strong> Cada
 * punto trae el acumulado desde el origen del libro, porque eso es lo que un
 * saldo es: mover el {@code from} no cambia ninguno de los puntos que siguen
 * cayendo dentro.
 */
public record OverTimeResponse(
        LocalDate from, LocalDate to, List<SummaryResponse> points) {

    public static OverTimeResponse of(
            LocalDate from, LocalDate to, List<BookSummary> series) {

        return new OverTimeResponse(
                from, to, series.stream().map(SummaryResponse::of).toList());
    }
}

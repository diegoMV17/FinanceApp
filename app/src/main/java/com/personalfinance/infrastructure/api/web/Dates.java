package com.personalfinance.infrastructure.api.web;

import com.personalfinance.infrastructure.api.limit.RangeTooWideException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Qué fecha es hoy y qué rango se contesta cuando no se pide ninguno.
 *
 * <p>Vive en un solo sitio por la misma razón que {@link Answers}: el mismo
 * {@code orToday} estaba copiado en dos controladores y con el de estadísticas
 * serían tres. Tres copias de «qué día es hoy» es el camino corto a que dos
 * lecturas de la misma petición contesten dos días distintos.
 *
 * <p>El dominio sigue sin leer ningún reloj: sus consultas reciben la fecha. Es
 * aquí, en la infraestructura, donde se decide qué día es, y el reloj es el bean
 * en UTC que también usan los casos de uso.
 */
final class Dates {

    /**
     * Cuántos meses admite una serie.
     *
     * <p>Diez años. Cada punto de la serie de indicadores es una pasada por el
     * libro completo y el libro cubre de 2000 a 2099, así que sin tope una
     * petición de doscientos caracteres encarga mil doscientas pasadas y mil
     * doscientas filas de respuesta.
     */
    static final int MAX_MONTHS = 120;

    /** Los meses que se devuelven cuando no se pide un rango. */
    private static final int MONTHS_BY_DEFAULT = 12;

    /**
     * La fecha que se pidió, o la de hoy en el calendario del dueño.
     *
     * <p>La zona es un parámetro y no la del reloj. El reloj está en UTC porque
     * los instantes que se guardan tienen que estarlo, y de un instante en UTC
     * no sale la fecha correcta: a las 23:30 del 31 de octubre en Bogotá, para
     * UTC ya es el 1 de noviembre, y el resumen del cierre de mes contestaba
     * noviembre con los dos indicadores del mes en cero.
     *
     * <p>Que la zona entre por aquí y solo por aquí es el reparto: cambia la
     * fecha con la que se contesta y no cambia ni un dato guardado.
     */
    static LocalDate orToday(LocalDate asOf, Clock clock, ZoneId zone) {
        return asOf != null ? asOf : LocalDate.ofInstant(clock.instant(), zone);
    }

    /**
     * De dónde arranca una serie a la que no le dieron principio: el día 1 del
     * mes, doce meses atrás contando el de {@code to}.
     *
     * <p>Se elige un valor en vez de exigir el parámetro por lo mismo que con
     * el {@code asOf}: el servidor puede contestar algo razonable y decir en la
     * respuesta con qué contestó, que es más útil que un 400.
     */
    static LocalDate orAYearBefore(LocalDate from, LocalDate to) {
        return from != null
                ? from
                : to.minusMonths(MONTHS_BY_DEFAULT - 1L).withDayOfMonth(1);
    }

    /**
     * Rechaza un rango que abarque más meses de los que se admiten.
     *
     * <p>Un rango al revés sale de aquí sin queja: la cuenta da negativo, pasa
     * el tope, y de él se queja el dominio con {@code reversed-range}, que es
     * donde vive esa regla. Dos sitios diciendo lo mismo acabarían
     * contradiciéndose.
     */
    static void requireWithinTheSpan(LocalDate from, LocalDate to) {
        long months = ChronoUnit.MONTHS.between(
                from.withDayOfMonth(1), to.withDayOfMonth(1)) + 1;

        if (months > MAX_MONTHS) {
            throw new RangeTooWideException(months, MAX_MONTHS);
        }
    }

    private Dates() {
    }
}

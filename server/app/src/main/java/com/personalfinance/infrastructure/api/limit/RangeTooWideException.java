package com.personalfinance.infrastructure.api.limit;

/**
 * Una serie pidió más meses de los que se admiten.
 *
 * <p>El tope no es de gusto. Cada punto de la serie de indicadores es una
 * pasada por el libro completo, y el rango que admite el libro va de 2000 a
 * 2099: pedirlo entero son mil doscientos puntos, es decir mil doscientas
 * pasadas y mil doscientas filas de respuesta a cambio de una petición de
 * doscientos caracteres. Es el mismo razonamiento del tope de página, aplicado
 * al trabajo en vez de al tamaño.
 *
 * <p>Se rechaza en vez de recortar porque recortar cambiaría la pregunta en
 * silencio, y un gráfico que contesta otro periodo del que se pidió es un
 * gráfico equivocado con buena pinta.
 *
 * <p>Vive aquí y no en el dominio por lo que es: un límite del borde, como el
 * del cuerpo. El libro no tiene nada que decir sobre cuántos meses caben en una
 * respuesta.
 */
public final class RangeTooWideException extends RuntimeException {

    private final long months;
    private final int limit;

    public RangeTooWideException(long months, int limitInMonths) {
        super("la serie pedía %d meses y el tope son %d".formatted(months, limitInMonths));
        this.months = months;
        this.limit = limitInMonths;
    }

    public long months() {
        return months;
    }

    /** Cuántos meses admite una serie. */
    public int limit() {
        return limit;
    }
}

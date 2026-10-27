package com.personalfinance.application.read;

/**
 * Qué trozo de una lectura se pidió: la página y cuántos elementos.
 *
 * <p>HU24. Vive en la capa de lectura y no en el dominio porque una página es
 * presentación: el libro no sabe de páginas, y meterle una sería enseñarle algo
 * que no es contabilidad.
 *
 * <p>Esto es la segunda de las dos redes del tamaño de una respuesta. La de
 * adelante está en el borde, que rechaza con un 400 un {@code size} fuera de
 * rango y apunta a estas constantes en vez de repetir el número. Esta de atrás
 * <em>recorta</em> en vez de estallar, y es a propósito: un tamaño de página no
 * es un hecho del libro, así que no hay nada que un cliente deba corregir una
 * vez que el borde ya le dijo que se pasó. Lo que esta red garantiza es que
 * ninguna lectura pueda devolver una respuesta sin tope, aunque mañana alguien
 * escriba un endpoint y se olvide de las anotaciones.
 *
 * <p>Y hasta hoy ninguna lectura tenía tope: {@code GET /api/accounts/{id}/movements}
 * devolvía el historial completo. Con un libro de años, eso es una petición
 * corta a cambio de una respuesta enorme.
 */
public record PageRequest(int page, int size) {

    /** Lo que se devuelve si nadie pide un tamaño. */
    public static final int DEFAULT_SIZE = 50;

    /** El techo. Ni el borde ni esta clase dejan pasar más. */
    public static final int MAX_SIZE = 200;

    private static final int MIN_SIZE = 1;

    public PageRequest(int page, int size) {
        this.page = Math.max(0, page);
        this.size = Math.min(MAX_SIZE, Math.max(MIN_SIZE, size));
    }

    /**
     * Lo que llega del borde, donde los dos parámetros son opcionales.
     *
     * <p>Un parámetro ausente y un parámetro vacío son lo mismo, que es lo que
     * ya hace Spring con un {@code asOf} vacío: llega nulo.
     */
    public static PageRequest of(Integer page, Integer size) {
        return new PageRequest(
                page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    /** El índice del primer elemento, en {@code long} porque el producto desborda. */
    long firstItem() {
        return (long) page * size;
    }
}

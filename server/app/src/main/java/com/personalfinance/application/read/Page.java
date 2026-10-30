package com.personalfinance.application.read;

import java.util.List;
import java.util.Objects;

/**
 * Un trozo de una lectura, y cuántos elementos hay en total.
 *
 * <p>HU24. El total va aparte de los elementos porque sin él una página no se
 * puede ni dibujar: quien lee necesita saber si hay más, y cuántas páginas son.
 *
 * @param items lo que cayó en esta página, en el orden que dio el libro
 * @param page  la página que se contestó, la primera es la cero
 * @param size  el tamaño con el que se cortó, ya recortado al tope
 * @param total cuántos elementos había antes de cortar
 */
public record Page<T>(List<T> items, int page, int size, long total) {

    public Page {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    /**
     * Corta la lista en un solo sitio.
     *
     * <p>El corte se calcula en {@code long}: {@code page * size} con una página
     * grande desborda el {@code int} y un índice negativo no se lee como "más
     * allá del final" sino como una excepción, o peor, como otra página. Una
     * página más allá del final es vacía y no un error: es lo que contesta un
     * cliente que pidió la siguiente cuando ya no quedaba ninguna.
     */
    public static <T> Page<T> of(List<T> all, PageRequest asked) {
        Objects.requireNonNull(all, "all");
        Objects.requireNonNull(asked, "asked");

        long total = all.size();
        long from = Math.min(asked.firstItem(), total);
        long to = Math.min(from + asked.size(), total);

        return new Page<>(
                all.subList((int) from, (int) to), asked.page(), asked.size(), total);
    }
}

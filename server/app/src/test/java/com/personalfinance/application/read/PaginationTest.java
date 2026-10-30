package com.personalfinance.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bloque L: la capa de lectura, sin Spring y sin Docker.
 *
 * <p>Corre donde no hay contenedor a propósito, por lo mismo que la lógica de
 * seguridad del borde: los tests de integración se saltan si no hay Docker, y
 * el tope del tamaño de una respuesta es justo lo que no puede quedar sin
 * probar en un build verde.
 */
@DisplayName("L · paginación de las lecturas")
class PaginationTest {

    private static final List<Integer> HUNDRED =
            IntStream.range(0, 100).boxed().toList();

    @Test
    @DisplayName("L1 · sin página ni tamaño contesta la primera, con el tamaño por omisión")
    void defaultsToTheFirstPage() {
        PageRequest asked = PageRequest.of(null, null);

        assertThat(asked.page()).isZero();
        assertThat(asked.size()).isEqualTo(PageRequest.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("L2 · un tamaño fuera de rango se recorta a los extremos")
    void clampsTheSize() {
        assertThat(PageRequest.of(0, PageRequest.MAX_SIZE + 1).size())
                .isEqualTo(PageRequest.MAX_SIZE);
        assertThat(PageRequest.of(0, Integer.MAX_VALUE).size())
                .isEqualTo(PageRequest.MAX_SIZE);
        assertThat(PageRequest.of(0, 0).size()).isEqualTo(1);
        assertThat(PageRequest.of(0, -5).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("L3 · la última página trae lo que queda, no un tamaño completo")
    void theLastPageIsShort() {
        Page<Integer> page = Page.of(HUNDRED, new PageRequest(3, 30));

        assertThat(page.items()).containsExactly(90, 91, 92, 93, 94, 95, 96, 97, 98, 99);
        assertThat(page.total()).isEqualTo(100);
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(30);
    }

    @Test
    @DisplayName("L4 · una página más allá del final es vacía, y sigue diciendo el total")
    void pastTheEndIsEmpty() {
        Page<Integer> page = Page.of(HUNDRED, new PageRequest(7, 30));

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(100);
    }

    @Test
    @DisplayName("L5 · una página negativa se lee como la primera")
    void clampsThePage() {
        assertThat(PageRequest.of(-1, 10).page()).isZero();
        assertThat(Page.of(HUNDRED, new PageRequest(-1, 10)).items())
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    @DisplayName("L6 · una página absurdamente alta no desborda ni devuelve basura")
    void doesNotOverflow() {
        Page<Integer> page = Page.of(HUNDRED, new PageRequest(Integer.MAX_VALUE, 200));

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(100);
    }

    /**
     * El que mata el desplazamiento de uno en el corte. Un {@code (page + 1)}
     * de más o de menos no se nota mirando una página suelta: se nota cuando
     * las páginas, puestas en fila, tienen que reconstruir la lista entera sin
     * repetir ni saltarse nada.
     */
    @Test
    @DisplayName("L7 · recorrer todas las páginas reconstruye la lista, sin repetir ni saltarse nada")
    void thePagesTileTheList() {
        List<Integer> walked = new ArrayList<>();
        int size = 7;

        for (int number = 0; ; number++) {
            Page<Integer> page = Page.of(HUNDRED, new PageRequest(number, size));
            if (page.items().isEmpty()) {
                break;
            }
            walked.addAll(page.items());
        }

        assertThat(walked).isEqualTo(HUNDRED);
    }

    @Test
    @DisplayName("L8 · una lista vacía es una página vacía, no un fallo")
    void emptyIsAPage() {
        Page<Integer> page = Page.of(List.of(), PageRequest.of(null, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }
}

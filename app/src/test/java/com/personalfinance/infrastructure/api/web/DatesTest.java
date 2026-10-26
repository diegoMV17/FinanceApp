package com.personalfinance.infrastructure.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalfinance.infrastructure.config.TimeZoneProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * H16. Qué día es hoy, sin Spring y sin Docker.
 *
 * <p>Esto es contrato HTTP aunque no haya HTTP a la vista: es la fecha con la
 * que la API contesta cuando no se le da ninguna, y de ella salen el
 * {@code asOf} del resumen, el mes de «gastos del mes» y el rango por omisión
 * de las series. Se prueba aquí, sin contenedor, porque un build sin Docker se
 * salta los tests de integración y esto no puede quedar sin probar.
 */
@DisplayName("H16 · qué día es hoy")
class DatesTest {

    /**
     * El 31 de octubre a las 23:30 en Bogotá. En UTC ya es el 1 de noviembre,
     * que es exactamente el momento en que el error se nota.
     */
    private static final Instant LATE_ON_THE_LAST_DAY = Instant.parse("2026-11-01T04:30:00Z");

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final Clock clock = Clock.fixed(LATE_ON_THE_LAST_DAY, ZoneOffset.UTC);

    /**
     * El reloj sigue en UTC y es la zona la que decide el día. Es el reparto
     * que importa: el instante que se guarda en el libro no cambia, y la fecha
     * que se le atribuye a un movimiento es la del calendario del dueño.
     */
    @Test
    @DisplayName("H16 · hoy es el día de la zona del dueño, no el de UTC")
    void todayBelongsToTheOwnersZone() {
        assertThat(Dates.orToday(null, clock, BOGOTA))
                .isEqualTo(LocalDate.of(2026, 10, 31));
    }

    /**
     * El error que esto arregla, escrito como test para que no vuelva: con la
     * fecha sacada de UTC, a las siete de la tarde en Bogotá ya es mañana. El
     * último día del mes eso mandaba «ingresos y gastos del mes» al mes
     * siguiente, en cero, justo el día en que se mira el cierre.
     */
    @Test
    @DisplayName("H16 · y con UTC habría contestado el mes siguiente, en cero")
    void utcWouldHaveAnsweredTheNextMonth() {
        assertThat(Dates.orToday(null, clock, ZoneOffset.UTC))
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("H16 · una fecha pedida se respeta tal cual, sin pasar por la zona")
    void anAskedDateIsLeftAlone() {
        LocalDate asked = LocalDate.of(2026, 3, 15);

        assertThat(Dates.orToday(asked, clock, BOGOTA)).isEqualTo(asked);
        assertThat(Dates.orToday(asked, clock, ZoneOffset.UTC)).isEqualTo(asked);
    }

    @Nested
    @DisplayName("la zona configurada")
    class TheConfiguredZone {

        @Test
        @DisplayName("H16 · por omisión es la del dueño del libro")
        void defaultsToTheOwnersZone() {
            assertThat(new TimeZoneProperties(null).zone()).isEqualTo(BOGOTA);
        }

        @Test
        @DisplayName("H16 · una zona configurada se usa")
        void aConfiguredZoneIsUsed() {
            assertThat(new TimeZoneProperties("Europe/Madrid").zone())
                    .isEqualTo(ZoneId.of("Europe/Madrid"));
        }

        /**
         * Una zona inventada impide el arranque en vez de aparecer como fechas
         * raras meses después. Es la misma decisión que con el tope del
         * candado: lo que no se entiende se dice al arrancar.
         */
        @Test
        @DisplayName("H16 · una zona que no existe no deja arrancar")
        void anUnknownZoneRefusesToStart() {
            assertThatThrownBy(() -> new TimeZoneProperties("Marte/Olympus"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("finance.timezone");
        }

        @Test
        @DisplayName("H16 · y una zona en blanco tampoco")
        void aBlankZoneRefusesToStart() {
            assertThatThrownBy(() -> new TimeZoneProperties("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

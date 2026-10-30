package com.personalfinance.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * H17. La zona del dueño, cableada de punta a punta.
 *
 * <p>{@link com.personalfinance.infrastructure.api.web.DatesTest} prueba la
 * regla; esto prueba que la regla llegue hasta la respuesta. Son dos cosas
 * distintas y la segunda es la que se rompe en silencio: la propiedad se puede
 * leer bien y el controlador seguir preguntándole la fecha al reloj.
 *
 * <p>El reloj se fija en el 31 de octubre a las 23:30 de Bogotá, que en UTC ya
 * es el 1 de noviembre. Con la fecha sacada de UTC, el resumen del cierre de
 * mes contestaba noviembre con los dos indicadores del mes en cero.
 */
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(TodayAtTheEdgeTest.ClockLateOnHalloween.class)
@DisplayName("H17 · la fecha de hoy en la respuesta")
class TodayAtTheEdgeTest extends PostgresIntegrationTest {

    /**
     * Un reloj propio para este contexto, con otro nombre de bean y
     * {@code @Primary}: reemplazar el bean {@code clock} por otro con el mismo
     * nombre sería una redefinición, que Boot no permite por omisión.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ClockLateOnHalloween {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-01T04:30:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("H17 · el resumen del cierre de mes es de octubre, no de noviembre")
    void theLastDayOfTheMonthIsStillThatMonth() throws Exception {
        JsonNode body = read(ask("/api/summary"));

        assertThat(body.get("asOf").asString())
                .as("en Bogotá son las 23:30 del 31 de octubre")
                .isEqualTo("2026-10-31");
        assertThat(body.get("monthFrom").asString())
                .as("y por lo tanto el mes del que se habla es octubre")
                .isEqualTo("2026-10-01");
    }

    @Test
    @DisplayName("H17 · y las series arrancan en el mes de esa misma fecha")
    void theSeriesStartsOnTheSameDay() throws Exception {
        JsonNode body = read(ask("/api/stats/over-time"));

        assertThat(body.get("to").asString()).isEqualTo("2026-10-31");
        assertThat(body.get("from").asString())
                .as("doce meses contando el de hoy")
                .isEqualTo("2025-11-01");
    }

    private MvcResult ask(String path) throws Exception {
        return mvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN))
                .andReturn();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}

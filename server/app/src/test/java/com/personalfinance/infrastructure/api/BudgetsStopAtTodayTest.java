package com.personalfinance.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * H20. Un gasto con fecha futura no cuenta hasta su fecha, tampoco contra el
 * límite de su categoría.
 *
 * <p>El resumen cuenta «gastos del mes» desde el día 1 hasta {@code asOf}, y
 * los presupuestos contaban desde el día 1 hasta el último día del mes: el
 * mismo gasto fechado un 25 hacía que el porcentaje del límite y los gastos del
 * mes dijeran cosas distintas en la misma pantalla, y el que estaba mal era el
 * porcentaje.
 *
 * <p>El reloj se fija a mitad de mes, que es la única hora a la que esto se
 * puede ver: el día 31, hoy y el fin de mes son el mismo día y los dos números
 * coinciden por casualidad.
 */
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(BudgetsStopAtTodayTest.ClockInTheMiddleOfOctober.class)
@DisplayName("H20 · lo gastado del mes en curso llega hasta hoy")
class BudgetsStopAtTodayTest extends PostgresIntegrationTest {

    /** Mediodía del 15 de octubre en Bogotá, que en UTC son las cinco. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ClockInTheMiddleOfOctober {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-15T17:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final String THIS_MONTH = "2026-10";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID bank;
    private UUID food;

    @BeforeEach
    void aBookWithAnExpenseStillToCome() throws Exception {
        jdbc.execute(
                "TRUNCATE entries, transactions, audit_log, budgets, accounts, people CASCADE");

        bank = openAccount("ASSET", "Bancolombia");
        food = openAccount("EXPENSE", "Alimentación");
        UUID equity = openAccount("EQUITY", "Saldos iniciales");

        send("/api/movements", """
                {"type":"OPENING_BALANCE","id":"%s","date":"2026-09-01","account":"%s",
                 "equity":"%s","amountInCents":80000000}"""
                .formatted(UUID.randomUUID(), bank, equity));

        spend("2026-10-10", 3_000_000, "Almuerzo");
        spend("2026-10-25", 5_000_000, "Mercado de fin de mes");
    }

    @Test
    @DisplayName("H20 · un gasto fechado el 25 no se cuenta el 15")
    void anExpenseStillToComeDoesNotCountYet() throws Exception {
        setBudget(food, THIS_MONTH, 10_000_000);

        JsonNode line = read(ask("/api/budgets?month=" + THIS_MONTH)).get("budgets").get(0);

        assertThat(line.get("spentInCents").asLong())
                .as("el almuerzo del 10, y nada del mercado del 25")
                .isEqualTo(3_000_000L);
        assertThat(line.get("remainingInCents").asLong()).isEqualTo(7_000_000L);
        assertThat(line.get("percentageUsed").asLong()).isEqualTo(30);
        assertThat(line.get("nearTheLimit").asBoolean())
                .as("contando el futuro iría al 80% y avisaría sin que haya pasado nada")
                .isFalse();
    }

    /**
     * La razón por la que esto importa: los dos números se leen juntos. Que
     * coincidan se exige contra la respuesta del resumen y no contra una
     * constante, porque lo que se está arreglando es que digan lo mismo.
     */
    @Test
    @DisplayName("H20 · y el límite dice lo mismo que «gastos del mes» del resumen")
    void theBudgetAndTheSummaryAgree() throws Exception {
        setBudget(food, THIS_MONTH, 10_000_000);

        long spent = read(ask("/api/budgets?month=" + THIS_MONTH))
                .get("budgets").get(0).get("spentInCents").asLong();
        JsonNode summary = read(ask("/api/summary"));

        assertThat(summary.get("asOf").asString()).isEqualTo("2026-10-15");
        assertThat(spent).isEqualTo(summary.get("expensesThatMonthInCents").asLong());
    }

    @Test
    @DisplayName("H20 · sin mes pedido, el de hoy se acota igual")
    void theMonthChosenByTheServerIsBoundedToo() throws Exception {
        setBudget(food, THIS_MONTH, 10_000_000);

        JsonNode body = read(ask("/api/budgets"));

        assertThat(body.get("month").asString()).isEqualTo(THIS_MONTH);
        assertThat(body.get("budgets").get(0).get("spentInCents").asLong())
                .isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("H20 · un mes que ya pasó sigue contando hasta su último día")
    void aMonthThatIsOverStillCountsToItsEnd() throws Exception {
        spend("2026-09-28", 4_000_000, "Mercado de septiembre");
        setBudget(food, "2026-09", 10_000_000);

        JsonNode line = read(ask("/api/budgets?month=2026-09")).get("budgets").get(0);

        assertThat(line.get("spentInCents").asLong())
                .as("el 28 de septiembre ya ocurrió; acotarlo a hoy lo perdería")
                .isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("H20 · un mes que no ha empezado no ha gastado nada, y contesta")
    void aMonthThatHasNotStartedHasSpentNothing() throws Exception {
        spend("2026-11-10", 2_000_000, "Mercado de noviembre");
        setBudget(food, "2026-11", 10_000_000);

        MvcResult answer = ask("/api/budgets?month=2026-11");

        assertThat(answer.getResponse().getStatus())
                .as("acotar a hoy un mes que no ha empezado deja el rango al revés")
                .isEqualTo(200);
        assertThat(read(answer).get("budgets").get(0).get("spentInCents").asLong()).isZero();
    }

    private void spend(String date, long cents, String description) throws Exception {
        send("/api/movements", """
                {"type":"EXPENSE","id":"%s","date":"%s","description":"%s",
                 "paidFrom":"%s","category":"%s","amountInCents":%d}"""
                .formatted(UUID.randomUUID(), date, description, bank, food, cents));
    }

    private void setBudget(UUID category, String month, long cents) throws Exception {
        send("/api/budgets", """
                {"id":"%s","category":"%s","month":"%s","limitInCents":%d}"""
                .formatted(UUID.randomUUID(), category, month, cents));
    }

    private UUID openAccount(String type, String name) throws Exception {
        UUID id = UUID.randomUUID();
        send("/api/accounts", """
                {"id":"%s","type":"%s","name":"%s"}""".formatted(id, type, name));
        return id;
    }

    private void send(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("el montaje del libro: %s respondió %s",
                        path, result.getResponse().getContentAsString())
                .isEqualTo(201);
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

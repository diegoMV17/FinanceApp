package com.personalfinance.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.personalfinance.infrastructure.PostgresIntegrationTest;
import com.personalfinance.infrastructure.api.error.ApiError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * S12. Ningún error cuenta nada del libro.
 *
 * <p>Este es el test que justifica que {@link ApiError} sea una lista cerrada, y
 * está escrito como una <strong>lista blanca</strong> a propósito. Comprobar que
 * un cuerpo «no contiene Bancolombia» es un test que depende de que a alguien se
 * le haya ocurrido la fuga concreta. Comprobar que el {@code detail} es
 * <em>uno de los dieciocho textos declarados</em> no depende de la imaginación
 * de nadie: un mensaje del dominio, una traza o un nombre de tabla no pueden
 * estar en esa lista, así que cualquier fuga rompe el test sin que nadie la haya
 * previsto.
 *
 * <p>El libro se monta con valores centinela — un nombre de cuenta y una
 * descripción que no se parecen a nada — para que la lista negra del final sea
 * además concluyente: si una de esas cadenas aparece en una respuesta de error,
 * salió del libro.
 */
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Las respuestas de error")
class ErrorLeakTest extends PostgresIntegrationTest {

    private static final String TODAY = "2026-10-20";

    /** Nombres y textos que solo pueden venir de dentro del libro. */
    private static final String ACCOUNT_SENTINEL = "ÑOÑO-CENTINELA-BANCO";
    private static final String CATEGORY_SENTINEL = "CENTINELA-CATEGORIA";
    private static final String DESCRIPTION_SENTINEL = "CENTINELA-DESCRIPCION";
    private static final String PERSON_SENTINEL = "CENTINELA-PERSONA";
    private static final long AMOUNT_SENTINEL = 987_654_321L;

    /** Lo único que un cuerpo de error puede traer. */
    private static final Set<String> ALLOWED_MEMBERS =
            Set.of("type", "title", "status", "detail", "instance", "errorId");

    private static final List<String> NEVER = List.of(
            "Exception", "exception", "com.personalfinance", "org.springframework",
            "\tat ", "at com.", "select ", "SELECT ", "insert ", "INSERT ",
            "pg_", "audit_log", "hibernate", "Hibernate", "jdbc", "JDBC",
            "HikariPool", "postgres", "Caused by");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID bank;
    private UUID category;
    private UUID movement;
    private UUID person;
    private UUID owedToPerson;

    @BeforeEach
    void writeABookFullOfSentinels() throws Exception {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts, people CASCADE");

        bank = open("ASSET", ACCOUNT_SENTINEL);
        category = open("EXPENSE", CATEGORY_SENTINEL);
        UUID equity = open("EQUITY", "CENTINELA-PATRIMONIO");

        seed("/api/movements", """
                {"type":"OPENING_BALANCE","id":"%s","date":"%s","account":"%s",
                 "equity":"%s","amountInCents":%d}"""
                .formatted(UUID.randomUUID(), TODAY, bank, equity, AMOUNT_SENTINEL));

        movement = UUID.randomUUID();
        seed("/api/movements", """
                {"type":"EXPENSE","id":"%s","date":"%s","description":"%s",
                 "paidFrom":"%s","category":"%s","amountInCents":1000}"""
                .formatted(movement, TODAY, DESCRIPTION_SENTINEL, bank, category));

        person = UUID.randomUUID();
        seed("/api/people", """
                {"id":"%s","name":"%s"}""".formatted(person, PERSON_SENTINEL));

        owedToPerson = open("RECEIVABLE", "CENTINELA-DEUDA");
        seed("/api/people/" + person + "/accounts", """
                {"account":"%s"}""".formatted(owedToPerson));
    }

    @Test
    @DisplayName("S12 · ninguna respuesta de error se sale de la lista declarada")
    void noErrorEscapesTheDeclaredList() throws Exception {
        List<Probe> probes = everyWayToFail();
        assertThat(probes).as("hay fallos que provocar").hasSizeGreaterThan(25);

        Set<String> allowedTypes = Arrays.stream(ApiError.values())
                .map(ApiError::type)
                .collect(Collectors.toSet());
        Set<String> allowedDetails = Arrays.stream(ApiError.values())
                .map(ApiError::detail)
                .collect(Collectors.toSet());

        int checked = 0;
        for (Probe probe : probes) {
            MvcResult result = mvc.perform(probe.request()).andReturn();
            int status = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString();

            if (status < 400) {
                continue;
            }
            checked++;

            assertThat(result.getResponse().getContentType())
                    .as("%s respondió %d con un tipo que no es problem+json",
                            probe.name(), status)
                    .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            JsonNode problem = json.readTree(body);
            List<String> members = new ArrayList<>();
            problem.propertyNames().forEach(members::add);

            assertThat(members)
                    .as("%s trae campos que no se declararon", probe.name())
                    .isSubsetOf(ALLOWED_MEMBERS);

            assertThat(problem.get("type").asString())
                    .as("%s responde un type que no está en ApiError", probe.name())
                    .isIn(allowedTypes);

            assertThat(problem.get("detail").asString())
                    .as("%s responde un detail que no es ninguno de los textos fijos; "
                            + "si es el mensaje de una excepción, aquí está la fuga",
                            probe.name())
                    .isIn(allowedDetails);

            assertThat(body)
                    .as("%s filtró algo del libro", probe.name())
                    .doesNotContain(ACCOUNT_SENTINEL)
                    .doesNotContain(CATEGORY_SENTINEL)
                    .doesNotContain(DESCRIPTION_SENTINEL)
                    .doesNotContain(PERSON_SENTINEL)
                    .doesNotContain(String.valueOf(AMOUNT_SENTINEL))
                    .doesNotContain(TEST_API_TOKEN);

            assertThat(body)
                    .as("%s filtró algo de las tripas", probe.name())
                    .doesNotContain(NEVER.toArray(CharSequence[]::new));

            if (status == 500) {
                assertThat(members).as("%s es un 500 sin errorId", probe.name())
                        .contains("errorId");
            }
        }

        assertThat(checked)
                .as("se comprobaron respuestas de error de verdad")
                .isGreaterThan(25);
    }

    /** Un presupuesto en JSON, para las formas de equivocarse al ponerlo. */
    private static String budgetOn(Object category, String month, long cents) {
        return """
                {"id":"%s","category":"%s","month":"%s","limitInCents":%d}"""
                .formatted(UUID.randomUUID(), category, month, cents);
    }

    /** Una cuenta más que el tope del filtro, para que lo rechace por tamaño. */
    private static String elevenAccounts() {
        return java.util.stream.IntStream.range(0, 11)
                .mapToObj(unused -> "account=" + UUID.randomUUID())
                .collect(Collectors.joining("&"));
    }

    @Test
    @DisplayName("S12 · y ninguna respuesta de error es HTML")
    void noErrorIsEverHtml() throws Exception {
        for (Probe probe : everyWayToFail()) {
            MvcResult result = mvc.perform(probe.request()).andReturn();

            if (result.getResponse().getStatus() < 400) {
                continue;
            }
            assertThat(result.getResponse().getContentAsString())
                    .as("%s devolvió HTML", probe.name())
                    .doesNotContainIgnoringCase("<html")
                    .doesNotContainIgnoringCase("<body")
                    .doesNotContainIgnoringCase("whitelabel");
        }
    }

    /**
     * Las rutas que existen, cruzadas con las formas de equivocarse.
     *
     * <p>Se incluyen a propósito los dos extremos: pedir cosas que no existen y
     * pedir cosas que chocan con lo que sí está, porque las dos clases de error
     * se arman con datos distintos y solo la segunda tiene algo del libro que
     * filtrar.
     */
    private List<Probe> everyWayToFail() {
        UUID unknown = UUID.randomUUID();
        List<Probe> probes = new ArrayList<>();

        // Sin token, en las diez rutas.
        probes.add(new Probe("GET /summary sin token", get("/api/summary")));
        probes.add(new Probe("GET /balances sin token", get("/api/balances")));
        probes.add(new Probe("GET movimientos sin token",
                get("/api/accounts/" + bank + "/movements")));
        probes.add(new Probe("GET revisiones sin token",
                get("/api/movements/" + movement + "/revisions")));
        probes.add(new Probe("POST cuenta sin token",
                post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")));
        probes.add(new Probe("POST movimiento sin token",
                post("/api/movements").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")));
        probes.add(new Probe("GET personas sin token", get("/api/people")));
        probes.add(new Probe("GET persona sin token", get("/api/people/" + person)));
        probes.add(new Probe("POST persona sin token",
                post("/api/people").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")));
        probes.add(new Probe("POST vinculo sin token",
                post("/api/people/" + person + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")));

        // Token mal.
        probes.add(new Probe("token equivocado",
                get("/api/summary").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-el")));
        probes.add(new Probe("esquema equivocado",
                get("/api/summary").header(HttpHeaders.AUTHORIZATION, "Basic abc")));

        // Cosas que no existen.
        probes.add(new Probe("ruta inexistente", authed(get("/api/no-existe"))));
        probes.add(new Probe("cuenta inexistente",
                authed(get("/api/accounts/" + unknown + "/movements"))));
        probes.add(new Probe("movimiento inexistente",
                authed(get("/api/movements/" + unknown + "/revisions"))));
        probes.add(new Probe("id que no es uuid",
                authed(get("/api/accounts/no-soy-uuid/movements"))));
        probes.add(new Probe("fecha imposible",
                authed(get("/api/summary?asOf=2026-13-45"))));

        // El historial general y las formas de equivocarse en sus filtros.
        // Esta lista es blanca: una ruta nueva que no se agregue aquí no es una
        // ruta que pase el barrido, es una ruta que el barrido no mira.
        probes.add(new Probe("GET historial sin token", get("/api/movements")));
        probes.add(new Probe("historial con cuenta inexistente",
                authed(get("/api/movements?account=" + unknown))));
        probes.add(new Probe("historial con persona inexistente",
                authed(get("/api/movements?person=" + unknown))));
        probes.add(new Probe("historial con rango de fechas al reves",
                authed(get("/api/movements?from=2026-03-31&to=2026-03-01"))));
        probes.add(new Probe("historial con rango de valor al reves",
                authed(get("/api/movements?minInCents=500000&maxInCents=100"))));
        probes.add(new Probe("historial con fecha fuera del siglo",
                authed(get("/api/movements?from=1999-12-31"))));
        probes.add(new Probe("historial con fecha ilegible",
                authed(get("/api/movements?from=no-es-fecha"))));
        probes.add(new Probe("historial con tipo inventado",
                authed(get("/api/movements?type=BANANA"))));
        probes.add(new Probe("historial con pagina negativa",
                authed(get("/api/movements?page=-1"))));
        probes.add(new Probe("historial con tamano sobre el tope",
                authed(get("/api/movements?size=100000"))));
        probes.add(new Probe("historial con valor fuera de rango",
                authed(get("/api/movements?minInCents=9999999999999999"))));
        probes.add(new Probe("historial con demasiadas cuentas",
                authed(get("/api/movements?" + elevenAccounts()))));
        probes.add(new Probe("movimientos de una cuenta con tamano sobre el tope",
                authed(get("/api/accounts/" + bank + "/movements?size=100000"))));

        // Las dos series.
        probes.add(new Probe("GET serie sin token", get("/api/stats/over-time")));
        probes.add(new Probe("GET desglose sin token", get("/api/stats/by-account")));
        probes.add(new Probe("serie de un siglo",
                authed(get("/api/stats/over-time?from=2000-01-01&to=2099-12-31"))));
        probes.add(new Probe("serie al reves",
                authed(get("/api/stats/over-time?from=2026-12-31&to=2026-01-01"))));
        probes.add(new Probe("serie con fecha ilegible",
                authed(get("/api/stats/over-time?from=ayer"))));
        probes.add(new Probe("desglose con tipo inventado",
                authed(get("/api/stats/by-account?type=BANANA"))));
        probes.add(new Probe("desglose con cuenta inexistente",
                authed(get("/api/stats/by-account?account=" + unknown))));
        probes.add(new Probe("desglose al reves",
                authed(get("/api/stats/by-account?from=2026-12-31&to=2026-01-01"))));

        // Los presupuestos, las dos rutas.
        probes.add(new Probe("GET presupuestos sin token", get("/api/budgets")));
        probes.add(new Probe("POST presupuesto sin token",
                post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content("{}")));
        probes.add(new Probe("presupuesto sin campos",
                authed(post("/api/budgets")).contentType(MediaType.APPLICATION_JSON)
                        .content("{}")));
        probes.add(new Probe("presupuesto de una categoria inexistente",
                authed(post("/api/budgets")).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetOn(unknown, "2026-03", 50_000_000))));
        probes.add(new Probe("presupuesto sobre una cuenta que no es de gasto",
                authed(post("/api/budgets")).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetOn(bank, "2026-03", 50_000_000))));
        probes.add(new Probe("presupuesto con mes inventado",
                authed(post("/api/budgets")).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetOn(category, "2026-13", 50_000_000))));
        probes.add(new Probe("presupuesto con mes fuera del siglo",
                authed(post("/api/budgets")).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetOn(category, "1999-12", 50_000_000))));
        probes.add(new Probe("presupuesto con monto cero",
                authed(post("/api/budgets")).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetOn(category, "2026-03", 0))));
        probes.add(new Probe("lectura de presupuestos con mes inventado",
                authed(get("/api/budgets?month=marzo"))));

        // Metodos y tipos de contenido.
        probes.add(new Probe("metodo no admitido", authed(delete("/api/movements"))));
        probes.add(new Probe("tipo de contenido equivocado",
                authed(post("/api/movements")).contentType(MediaType.TEXT_PLAIN)
                        .content("hola")));
        probes.add(new Probe("sin tipo de contenido",
                authed(post("/api/movements")).content("{}")));

        // Cuerpos invalidos.
        probes.add(new Probe("json roto", jsonBody("/api/movements", "{roto")));
        probes.add(new Probe("campo desconocido",
                jsonBody("/api/movements", expense(UUID.randomUUID(), 1000, "x")
                        .replace("}", ",\"extra\":1}"))));
        probes.add(new Probe("monto decimal",
                jsonBody("/api/movements", """
                        {"type":"EXPENSE","id":"%s","date":"%s","description":"x",
                         "paidFrom":"%s","category":"%s","amountInCents":12.34}"""
                        .formatted(UUID.randomUUID(), TODAY, bank, category))));
        probes.add(new Probe("monto negativo",
                jsonBody("/api/movements", expense(UUID.randomUUID(), -5, "x"))));
        probes.add(new Probe("monto fuera de rango",
                jsonBody("/api/movements", expense(UUID.randomUUID(), 1_000_000_000_001L, "x"))));
        probes.add(new Probe("tipo de movimiento inventado",
                jsonBody("/api/movements", """
                        {"type":"INVENTADO","id":"%s","date":"%s","description":"x",
                         "paidFrom":"%s","category":"%s","amountInCents":1000}"""
                        .formatted(UUID.randomUUID(), TODAY, bank, category))));
        probes.add(new Probe("texto con control de direccion",
                jsonBody("/api/movements",
                        expense(UUID.randomUUID(), 1000, "x\\u202E"))));
        probes.add(new Probe("texto con salto de linea",
                jsonBody("/api/movements", expense(UUID.randomUUID(), 1000, "x\\n"))));
        probes.add(new Probe("descripcion larguisima",
                jsonBody("/api/movements",
                        expense(UUID.randomUUID(), 1000, "a".repeat(400)))));
        probes.add(new Probe("cuerpo enorme",
                jsonBody("/api/movements",
                        "{\"relleno\":\"" + "a".repeat(70_000) + "\"}")));

        // Choques con lo que si existe: aqui es donde hay algo que filtrar.
        probes.add(new Probe("movimiento repetido con otro contenido",
                jsonBody("/api/movements", expense(movement, 55_555, "otra cosa"))));
        probes.add(new Probe("nombre de cuenta ya usado",
                jsonBody("/api/accounts", """
                        {"id":"%s","type":"ASSET","name":"%s"}"""
                        .formatted(UUID.randomUUID(), ACCOUNT_SENTINEL))));
        probes.add(new Probe("archivar una cuenta con saldo",
                authed(post("/api/accounts/" + bank + "/archivals"))));
        probes.add(new Probe("cuenta de un tipo que no sirve ahi",
                jsonBody("/api/movements", """
                        {"type":"EXPENSE","id":"%s","date":"%s","description":"x",
                         "paidFrom":"%s","category":"%s","amountInCents":1000}"""
                        .formatted(UUID.randomUUID(), TODAY, category, bank))));
        probes.add(new Probe("renombrar a un nombre ya usado",
                jsonBody("/api/accounts/" + category + "/renames", """
                        {"newName":"%s"}""".formatted(CATEGORY_SENTINEL))));
        probes.add(new Probe("renombrar algo que no existe",
                jsonBody("/api/accounts/" + unknown + "/renames", """
                        {"newName":"Otra"}""")));
        probes.add(new Probe("anular algo que no existe",
                jsonBody("/api/movements/" + unknown + "/cancellations", """
                        {"reason":"porque si"}""")));
        probes.add(new Probe("corregir algo que no existe",
                jsonBody("/api/movements/" + unknown + "/amendments", """
                        {"correction":%s}""".formatted(
                                expense(UUID.randomUUID(), 1000, "x")))));

        // Las personas: las seis rutas del incremento 2, con algo que filtrar
        // en cada clase de error.
        probes.add(new Probe("persona inexistente", authed(get("/api/people/" + unknown))));
        probes.add(new Probe("id de persona que no es uuid",
                authed(get("/api/people/no-soy-uuid"))));
        probes.add(new Probe("nombre de persona ya usado",
                jsonBody("/api/people", """
                        {"id":"%s","name":"%s"}"""
                        .formatted(UUID.randomUUID(), PERSON_SENTINEL))));
        probes.add(new Probe("misma persona con otro nombre",
                jsonBody("/api/people", """
                        {"id":"%s","name":"Otra"}""".formatted(person))));
        probes.add(new Probe("nombre de persona en blanco",
                jsonBody("/api/people", """
                        {"id":"%s","name":"   "}""".formatted(UUID.randomUUID()))));
        probes.add(new Probe("nombre de persona con control de direccion",
                jsonBody("/api/people", """
                        {"id":"%s","name":"Juan\\u202E"}"""
                        .formatted(UUID.randomUUID()))));
        probes.add(new Probe("renombrar una persona que no existe",
                jsonBody("/api/people/" + unknown + "/renames", """
                        {"newName":"Otra"}""")));
        probes.add(new Probe("archivar una persona con cuentas abiertas",
                authed(post("/api/people/" + person + "/archivals"))));
        probes.add(new Probe("archivar una persona que no existe",
                authed(post("/api/people/" + unknown + "/archivals"))));
        probes.add(new Probe("vincular una categoria",
                jsonBody("/api/people/" + person + "/accounts", """
                        {"account":"%s"}""".formatted(category))));
        probes.add(new Probe("vincular a una persona que no existe",
                jsonBody("/api/people/" + unknown + "/accounts", """
                        {"account":"%s"}""".formatted(owedToPerson))));
        probes.add(new Probe("vincular una cuenta que no existe",
                jsonBody("/api/people/" + person + "/accounts", """
                        {"account":"%s"}""".formatted(unknown))));
        probes.add(new Probe("vincular sin decir la cuenta",
                jsonBody("/api/people/" + person + "/accounts", "{}")));

        return probes;
    }

    private String expense(UUID id, long cents, String description) {
        return """
                {"type":"EXPENSE","id":"%s","date":"%s","description":"%s",
                 "paidFrom":"%s","category":"%s","amountInCents":%d}"""
                .formatted(id, TODAY, description, bank, category, cents);
    }

    private MockHttpServletRequestBuilder jsonBody(String path, String body) {
        return authed(post(path)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN);
    }

    private UUID open(String type, String name) throws Exception {
        UUID id = UUID.randomUUID();
        seed("/api/accounts",
                """
                {"id":"%s","type":"%s","name":"%s"}""".formatted(id, type, name));
        return id;
    }

    private void seed(String path, String body) throws Exception {
        MvcResult result = mvc.perform(jsonBody(path, body)).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("montando el libro con %s: %s", path,
                        result.getResponse().getContentAsString())
                .isEqualTo(201);
    }

    /** Una forma de equivocarse, con un nombre para cuando falle. */
    private record Probe(String name, MockHttpServletRequestBuilder request) {
    }
}

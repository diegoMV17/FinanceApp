package com.personalfinance.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * S11. Lo que la API se niega a leer.
 *
 * <p>Cada uno de estos casos es un valor por omisión que habría hecho lo
 * contrario. Jackson 3 ignora los campos desconocidos si nadie se lo prohíbe, y
 * acepta un decimal donde se pidió un entero truncándolo en silencio; Tomcat no
 * acota el cuerpo de un JSON; y {@code Character.isISOControl} no cuenta como
 * de control ninguno de los invisibles que dan la vuelta al texto.
 *
 * <p>El caso del monto decimal es el que más importa. {@code 1234.56} centavos
 * se habría guardado como {@code 1234} sin un solo aviso: no es un problema de
 * formato, es plata que desaparece.
 */
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Lo que la API no lee")
class JsonStrictnessTest extends PostgresIntegrationTest {

    private static final String TODAY = "2026-10-20";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID bank;
    private UUID food;

    @BeforeEach
    void startFromAnEmptyBook() throws Exception {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts CASCADE");
        bank = openAccount("ASSET", "Bancolombia");
        food = openAccount("EXPENSE", "Alimentación");
    }

    @Nested
    @DisplayName("del cuerpo")
    class OfTheBody {

        @Test
        @DisplayName("S11 · un monto con decimales responde 400, no se trunca")
        void aDecimalAmountIsRefused() throws Exception {
            MvcResult refused = send("/api/movements", """
                    {"type":"EXPENSE","id":"%s","date":"%s","description":"Almuerzo",
                     "paidFrom":"%s","category":"%s","amountInCents":1234.56}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, food));

            assertThat(refused.getResponse().getStatus()).isEqualTo(400);

            // Y no quedó nada escrito: si se hubiera truncado, aquí habría un
            // movimiento de 1234 centavos que nadie pidió.
            assertThat(countMovements()).isZero();
        }

        @Test
        @DisplayName("S11 · un campo que nadie declaró responde 400")
        void anUnknownFieldIsRefused() throws Exception {
            MvcResult refused = send("/api/movements", """
                    {"type":"EXPENSE","id":"%s","date":"%s","description":"Almuerzo",
                     "paidFrom":"%s","category":"%s","amountInCents":1000,
                     "campoQueNadieDeclaro":"hola"}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, food));

            assertThat(refused.getResponse().getStatus()).isEqualTo(400);
            assertThat(countMovements()).isZero();
        }

        @Test
        @DisplayName("S11 · el cliente no puede fijar el origen")
        void theClientCannotSetTheSource() throws Exception {
            // El origen lo decide quien registra, no quien pide. Que sea un
            // campo de solo lectura se hace cumplir solo: "source" es un campo
            // desconocido en la entrada.
            MvcResult refused = send("/api/movements", """
                    {"type":"EXPENSE","id":"%s","date":"%s","description":"Almuerzo",
                     "paidFrom":"%s","category":"%s","amountInCents":1000,
                     "source":"BOT"}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, food));

            assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("S11 · un tipo de movimiento que no existe responde 400")
        void anUnknownMovementTypeIsRefused() throws Exception {
            MvcResult refused = send("/api/movements", """
                    {"type":"ROBAR_UN_BANCO","id":"%s","date":"%s","description":"x",
                     "paidFrom":"%s","category":"%s","amountInCents":1000}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, food));

            assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        }

        @Test
        @DisplayName("S11 · un monto ausente o no positivo responde 400")
        void amissingOrNonPositiveAmountIsRefused() throws Exception {
            // Un long ausente se vuelve 0 calladamente, asi que el @Positive es
            // lo que lo atrapa. Los tres casos van juntos porque es el mismo
            // agujero visto desde tres lados.
            String withoutAmount = """
                    {"type":"EXPENSE","id":"%s","date":"%s","description":"Almuerzo",
                     "paidFrom":"%s","category":"%s"}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, food);

            assertThat(send("/api/movements", withoutAmount).getResponse().getStatus())
                    .isEqualTo(400);
            assertThat(send("/api/movements", expenseWith(0)).getResponse().getStatus())
                    .isEqualTo(400);
            assertThat(send("/api/movements", expenseWith(-5000)).getResponse().getStatus())
                    .isEqualTo(400);
        }

        @Test
        @DisplayName("S11 · un monto por encima del límite del dominio responde 400")
        void anAmountOverTheDomainLimitIsRefused() throws Exception {
            assertThat(send("/api/movements", expenseWith(1_000_000_000_001L))
                            .getResponse().getStatus())
                    .isEqualTo(400);
        }

        @Test
        @DisplayName("S11 · un JSON roto responde 400")
        void brokenJsonIsRefused() throws Exception {
            assertThat(send("/api/movements", "{esto no es json").getResponse().getStatus())
                    .isEqualTo(400);
            assertThat(send("/api/movements", "").getResponse().getStatus())
                    .isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("del texto")
    class OfTheText {

        @Test
        @DisplayName("S11 · un salto de línea al final responde 400")
        void atrailingNewlineIsRefused() throws Exception {
            // Este es el caso que trim() se comería: si el largo se midiera
            // antes de buscar los caracteres de control, "Almuerzo\\n" entraría
            // y lo guardado no sería lo que llegó.
            MvcResult refused = send("/api/movements", descriptionOf("Almuerzo\\n"));

            assertThat(refused.getResponse().getStatus()).isEqualTo(400);
            assertThat(countMovements()).isZero();
        }

        @Test
        @DisplayName("S11 · un control de dirección responde 400 con su propio código")
        void abidiControlIsRefused() throws Exception {
            MvcResult refused = send("/api/movements", descriptionOf("Almuerzo\\u202E"));

            assertThat(refused.getResponse().getStatus()).isEqualTo(400);
            assertThat(json.readTree(refused.getResponse().getContentAsString())
                            .get("type").asString())
                    .isEqualTo("urn:finance:error:unsafe-text");
        }

        @Test
        @DisplayName("S11 · y los demás invisibles también")
        void theOtherInvisiblesToo() throws Exception {
            String[] invisibles = {"\\u0000", "\\u2028", "\\u2029", "\\u200B", "\\uFEFF"};

            for (String invisible : invisibles) {
                MvcResult refused = send("/api/movements",
                        descriptionOf("Almuerzo" + invisible));

                assertThat(refused.getResponse().getStatus())
                        .as("con %s", invisible)
                        .isEqualTo(400);
            }
            assertThat(countMovements()).isZero();
        }

        @Test
        @DisplayName("S11 · pero el español de verdad entra")
        void butRealSpanishGetsIn() throws Exception {
            MvcResult accepted = send("/api/movements",
                    descriptionOf("Almuerzo en el café — con Mamá"));

            assertThat(accepted.getResponse().getStatus()).isEqualTo(201);
        }

        @Test
        @DisplayName("S11 · una descripción más larga que el límite responde 400")
        void atooLongDescriptionIsRefused() throws Exception {
            assertThat(send("/api/movements", descriptionOf("a".repeat(201)))
                            .getResponse().getStatus())
                    .isEqualTo(400);
            assertThat(send("/api/movements", descriptionOf("a".repeat(200)))
                            .getResponse().getStatus())
                    .isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("del sobre")
    class OfTheEnvelope {

        @Test
        @DisplayName("S11 · otro tipo de contenido responde 415")
        void anotherContentTypeIsRefused() throws Exception {
            MvcResult refused = mvc.perform(post("/api/movements")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("{}"))
                    .andReturn();

            assertThat(refused.getResponse().getStatus()).isEqualTo(415);
            assertThat(json.readTree(refused.getResponse().getContentAsString())
                            .get("type").asString())
                    .isEqualTo("urn:finance:error:unsupported-media-type");
        }

        @Test
        @DisplayName("S11 · sin tipo de contenido también")
        void withoutAContentTypeToo() throws Exception {
            MvcResult refused = mvc.perform(post("/api/movements")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN)
                            .content("{}"))
                    .andReturn();

            assertThat(refused.getResponse().getStatus()).isEqualTo(415);
        }

        @Test
        @DisplayName("S11 · un método que esa ruta no admite responde 405")
        void anUnsupportedMethodIs405() throws Exception {
            MvcResult refused = mvc.perform(delete("/api/movements")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN))
                    .andReturn();

            assertThat(refused.getResponse().getStatus()).isEqualTo(405);
            // Nada se borra en este libro, y el verbo que lo dice no existe.
            assertThat(json.readTree(refused.getResponse().getContentAsString())
                            .get("type").asString())
                    .isEqualTo("urn:finance:error:method-not-allowed");
        }

        @Test
        @DisplayName("S11 · un cuerpo enorme responde 413 sin leerse")
        void ahugeBodyIsRefused() throws Exception {
            // Por encima del tope de 64 KiB. El filtro lo corta mirando el
            // Content-Length, antes de que nadie lea un byte.
            String huge = "{\"relleno\":\"" + "a".repeat(70_000) + "\"}";

            MvcResult refused = send("/api/movements", huge);

            assertThat(refused.getResponse().getStatus()).isEqualTo(413);
            assertThat(json.readTree(refused.getResponse().getContentAsString())
                            .get("type").asString())
                    .isEqualTo("urn:finance:error:content-too-large");
        }

        @Test
        @DisplayName("S11 · y el tope va antes de autenticar")
        void theSizeCapComesBeforeAuthentication() throws Exception {
            // Sin token: la respuesta tiene que ser 413 y no 401, porque de lo
            // contrario alguien sin token podria hacer que el proceso lea un
            // cuerpo enorme solo para rechazarlo despues.
            String huge = "{\"relleno\":\"" + "a".repeat(70_000) + "\"}";

            MvcResult refused = mvc.perform(post("/api/movements")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(huge))
                    .andReturn();

            assertThat(refused.getResponse().getStatus()).isEqualTo(413);
        }
    }

    private String descriptionOf(String description) {
        return """
                {"type":"EXPENSE","id":"%s","date":"%s","description":"%s",
                 "paidFrom":"%s","category":"%s","amountInCents":1000}"""
                .formatted(UUID.randomUUID(), TODAY, description, bank, food);
    }

    private String expenseWith(long cents) {
        return """
                {"type":"EXPENSE","id":"%s","date":"%s","description":"Almuerzo",
                 "paidFrom":"%s","category":"%s","amountInCents":%d}"""
                .formatted(UUID.randomUUID(), TODAY, bank, food, cents);
    }

    private int countMovements() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class);
        return count == null ? 0 : count;
    }

    private UUID openAccount(String type, String name) throws Exception {
        UUID id = UUID.randomUUID();
        MvcResult opened = send("/api/accounts",
                """
                {"id":"%s","type":"%s","name":"%s"}""".formatted(id, type, name));
        assertThat(opened.getResponse().getStatus()).isEqualTo(201);
        return id;
    }

    private MvcResult send(String path, String body) throws Exception {
        return mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }
}

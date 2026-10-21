package com.personalfinance.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * H3. Las seis escrituras, por HTTP.
 *
 * <p>Lo que se comprueba en cada una es siempre lo mismo, porque es la forma
 * que tienen todas: el primer intento escribe y responde 201; el mismo id con
 * el mismo contenido no escribe nada y responde 200 con el cuerpo idéntico; el
 * mismo id con otro contenido es una contradicción y responde 409.
 *
 * <p>Esa comparación «cuerpo idéntico» se hace sobre la cadena cruda a
 * propósito. Campo por campo dejaría pasar que el reintento devolviera algo
 * parecido pero no lo mismo, y el sentido de un reintento es que el cliente que
 * perdió la primera respuesta pueda confiar en la segunda.
 *
 * <p>Las cuentas del fixture se abren por la propia API, no por los casos de
 * uso: así el montaje también es una prueba de que abrir cuentas funciona.
 */
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Las escrituras por HTTP")
class WriteEndpointsTest extends PostgresIntegrationTest {

    private static final String TODAY = "2026-10-20";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID bank;
    private UUID food;
    private UUID equity;
    private UUID brother;

    @BeforeEach
    void startFromAnEmptyBook() throws Exception {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts CASCADE");

        bank = openAccount("ASSET", "Bancolombia");
        food = openAccount("EXPENSE", "Alimentación");
        equity = openAccount("EQUITY", "Saldos iniciales");
        brother = openAccount("PAYABLE", "Hermano");
    }

    @Nested
    @DisplayName("al abrir una cuenta")
    class WhenOpeningAnAccount {

        @Test
        @DisplayName("H3 · la primera vez responde 201 con la cuenta")
        void theFirstTimeIs201() throws Exception {
            UUID id = UUID.randomUUID();

            MvcResult created = send("/api/accounts", """
                    {"id":"%s","type":"ASSET","name":"Nequi"}""".formatted(id));

            assertThat(created.getResponse().getStatus()).isEqualTo(201);
            JsonNode body = read(created);
            assertThat(body.get("id").asString()).isEqualTo(id.toString());
            assertThat(body.get("type").asString()).isEqualTo("ASSET");
            assertThat(body.get("name").asString()).isEqualTo("Nequi");
            assertThat(body.get("archivedAt").isNull()).isTrue();
        }

        @Test
        @DisplayName("H3 · repetirla igual responde 200 con el mismo cuerpo")
        void repeatingItIs200() throws Exception {
            UUID id = UUID.randomUUID();
            String request = """
                    {"id":"%s","type":"ASSET","name":"Nequi"}""".formatted(id);

            MvcResult first = send("/api/accounts", request);
            MvcResult again = send("/api/accounts", request);

            assertThat(first.getResponse().getStatus()).isEqualTo(201);
            assertThat(again.getResponse().getStatus()).isEqualTo(200);
            assertThat(again.getResponse().getContentAsString())
                    .isEqualTo(first.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("H3 · el mismo id con otro nombre responde 409")
        void thesameIdWithOtherContentIs409() throws Exception {
            UUID id = UUID.randomUUID();
            send("/api/accounts", """
                    {"id":"%s","type":"ASSET","name":"Nequi"}""".formatted(id));

            MvcResult conflict = send("/api/accounts", """
                    {"id":"%s","type":"ASSET","name":"Daviplata"}""".formatted(id));

            assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
            assertThat(read(conflict).get("type").asString())
                    .isEqualTo("urn:finance:error:duplicate-id");
        }

        @Test
        @DisplayName("H3 · un nombre ya usado en el mismo tipo responde 409")
        void aTakenNameIs409() throws Exception {
            MvcResult conflict = send("/api/accounts", """
                    {"id":"%s","type":"ASSET","name":"Bancolombia"}"""
                    .formatted(UUID.randomUUID()));

            assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
            assertThat(read(conflict).get("type").asString())
                    .isEqualTo("urn:finance:error:duplicate-account-name");
        }
    }

    @Nested
    @DisplayName("al renombrar")
    class WhenRenaming {

        @Test
        @DisplayName("H3 · cambia el nombre y responde 201")
        void itRenamesAndAnswers201() throws Exception {
            MvcResult renamed = send("/api/accounts/" + bank + "/renames",
                    """
                    {"newName":"Bancolombia Ahorros"}""");

            assertThat(renamed.getResponse().getStatus()).isEqualTo(201);
            assertThat(read(renamed).get("name").asString()).isEqualTo("Bancolombia Ahorros");
        }

        @Test
        @DisplayName("H3 · arreglar solo las mayúsculas también renombra")
        void fixingTheCaseIsStillARename() throws Exception {
            UUID nequi = openAccount("ASSET", "nequi");

            MvcResult renamed = send("/api/accounts/" + nequi + "/renames",
                    """
                    {"newName":"Nequi"}""");

            // answersTo ignora mayúsculas, isNamed no. Si el caso de uso usara
            // el primero, esto responderia 200 sin haber cambiado nada.
            assertThat(renamed.getResponse().getStatus()).isEqualTo(201);
            assertThat(read(renamed).get("name").asString()).isEqualTo("Nequi");
        }

        @Test
        @DisplayName("H3 · el mismo nombre que ya tenía responde 200")
        void thesameNameIs200() throws Exception {
            MvcResult same = send("/api/accounts/" + bank + "/renames",
                    """
                    {"newName":"Bancolombia"}""");

            assertThat(same.getResponse().getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("H3 · una cuenta que no existe responde 404")
        void anUnknownAccountIs404() throws Exception {
            MvcResult missing = send("/api/accounts/" + UUID.randomUUID() + "/renames",
                    """
                    {"newName":"Nequi"}""");

            assertThat(missing.getResponse().getStatus()).isEqualTo(404);
            assertThat(read(missing).get("type").asString())
                    .isEqualTo("urn:finance:error:not-found");
        }
    }

    @Nested
    @DisplayName("al archivar")
    class WhenArchiving {

        @Test
        @DisplayName("H3 · una cuenta en cero se archiva con su fecha")
        void anEmptyAccountIsArchived() throws Exception {
            UUID spare = openAccount("ASSET", "Cuenta vieja");

            MvcResult archived = mvc.perform(post("/api/accounts/" + spare + "/archivals")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN))
                    .andReturn();

            assertThat(archived.getResponse().getStatus()).isEqualTo(201);
            assertThat(read(archived).get("archivedAt").asString()).endsWith("Z");
        }

        @Test
        @DisplayName("H3 · archivarla otra vez responde 200 con la misma fecha")
        void archivingTwiceIs200() throws Exception {
            UUID spare = openAccount("ASSET", "Cuenta vieja");
            String path = "/api/accounts/" + spare + "/archivals";

            MvcResult first = archive(path);
            MvcResult again = archive(path);

            assertThat(first.getResponse().getStatus()).isEqualTo(201);
            assertThat(again.getResponse().getStatus()).isEqualTo(200);
            assertThat(again.getResponse().getContentAsString())
                    .isEqualTo(first.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("H3 · una cuenta con saldo responde 409")
        void anAccountWithMoneyIs409() throws Exception {
            send("/api/movements", """
                    {"type":"OPENING_BALANCE","id":"%s","date":"%s",
                     "account":"%s","equity":"%s","amountInCents":80000000}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, equity));

            MvcResult refused = archive("/api/accounts/" + bank + "/archivals");

            assertThat(refused.getResponse().getStatus()).isEqualTo(409);
            assertThat(read(refused).get("type").asString())
                    .isEqualTo("urn:finance:error:account-still-holds-money");
        }

        private MvcResult archive(String path) throws Exception {
            return mvc.perform(post(path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN))
                    .andReturn();
        }
    }

    @Nested
    @DisplayName("al registrar un movimiento")
    class WhenRecordingAMovement {

        @Test
        @DisplayName("H3 · un gasto queda con sus dos apuntes que suman cero")
        void anExpenseHasTwoBalancedEntries() throws Exception {
            UUID id = UUID.randomUUID();

            MvcResult recorded = send("/api/movements", expense(id, 2_500_000));

            assertThat(recorded.getResponse().getStatus()).isEqualTo(201);
            JsonNode body = read(recorded);
            assertThat(body.get("id").asString()).isEqualTo(id.toString());
            assertThat(body.get("description").asString()).isEqualTo("Almuerzo");
            assertThat(body.get("source").asString()).isEqualTo("APP");
            assertThat(body.get("replaces").isNull()).isTrue();

            JsonNode entries = body.get("entries");
            assertThat(entries.size()).isEqualTo(2);
            long sum = 0;
            for (JsonNode entry : entries) {
                sum += entry.get("amountInCents").asLong();
            }
            assertThat(sum).as("la partida doble suma cero").isZero();
        }

        @Test
        @DisplayName("H3 · repetirlo igual responde 200 con el mismo cuerpo")
        void replayingIt() throws Exception {
            UUID id = UUID.randomUUID();

            MvcResult first = send("/api/movements", expense(id, 2_500_000));
            MvcResult again = send("/api/movements", expense(id, 2_500_000));

            assertThat(first.getResponse().getStatus()).isEqualTo(201);
            assertThat(again.getResponse().getStatus()).isEqualTo(200);
            assertThat(again.getResponse().getContentAsString())
                    .isEqualTo(first.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("H3 · con otro monto bajo el mismo id responde 409")
        void changingTheAmountIs409() throws Exception {
            UUID id = UUID.randomUUID();
            send("/api/movements", expense(id, 2_500_000));

            MvcResult conflict = send("/api/movements", expense(id, 9_900_000));

            assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
            assertThat(read(conflict).get("type").asString())
                    .isEqualTo("urn:finance:error:duplicate-id");
        }

        @Test
        @DisplayName("H3 · con una cuenta que no existe responde 404")
        void anUnknownAccountIs404() throws Exception {
            MvcResult missing = send("/api/movements", """
                    {"type":"EXPENSE","id":"%s","date":"%s","description":"Almuerzo",
                     "paidFrom":"%s","category":"%s","amountInCents":1000}"""
                    .formatted(UUID.randomUUID(), TODAY, UUID.randomUUID(), food));

            assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        }

        @Test
        @DisplayName("H3 · los once tipos se registran")
        void allElevenTypesWork() throws Exception {
            // Un movimiento de cada tipo, para que ninguno quede sin pasar por
            // HTTP ni una vez. Si uno falta en @JsonSubTypes, aquí se cae.
            UUID juan = openAccount("RECEIVABLE", "Juan");
            UUID salary = openAccount("INCOME", "Salario");
            UUID momsFund = openAccount("THIRD_PARTY_FUND", "Mamá");
            UUID cash = openAccount("ASSET", "Efectivo");

            send("/api/movements", """
                    {"type":"OPENING_BALANCE","id":"%s","date":"%s","account":"%s",
                     "equity":"%s","amountInCents":80000000}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, equity));

            record Case(String type, String a, String aValue, String b, String bValue) {
            }

            Case[] cases = {
                new Case("INCOME", "receivedInto", bank.toString(), "category", salary.toString()),
                new Case("EXPENSE", "paidFrom", bank.toString(), "category", food.toString()),
                new Case("TRANSFER", "from", bank.toString(), "to", cash.toString()),
                new Case("LEND", "paidFrom", bank.toString(), "borrower", juan.toString()),
                new Case("COLLECT_LOAN", "borrower", juan.toString(), "receivedInto", bank.toString()),
                new Case("BORROW", "lender", brother.toString(), "receivedInto", bank.toString()),
                new Case("REPAY_DEBT", "paidFrom", bank.toString(), "lender", brother.toString()),
                new Case("RECEIVE_THIRD_PARTY_MONEY", "fund", momsFund.toString(), "heldIn", bank.toString()),
                new Case("SPEND_THIRD_PARTY_MONEY", "fund", momsFund.toString(), "paidFrom", bank.toString()),
                new Case("RETURN_THIRD_PARTY_MONEY", "fund", momsFund.toString(), "paidFrom", bank.toString()),
            };

            for (Case one : cases) {
                MvcResult result = send("/api/movements", """
                        {"type":"%s","id":"%s","date":"%s","description":"Prueba de %s",
                         "%s":"%s","%s":"%s","amountInCents":100000}"""
                        .formatted(one.type(), UUID.randomUUID(), TODAY, one.type(),
                                one.a(), one.aValue(), one.b(), one.bValue()));

                assertThat(result.getResponse().getStatus())
                        .as("%s respondió %s: %s", one.type(),
                                result.getResponse().getStatus(),
                                result.getResponse().getContentAsString())
                        .isEqualTo(201);
            }
        }

        @Test
        @DisplayName("H3 · un saldo inicial con descripción responde 400")
        void anOpeningBalanceWithADescriptionIs400() throws Exception {
            // OPENING_BALANCE no tiene descripción: la pone la operación. Un
            // campo de más no se ignora en silencio.
            MvcResult refused = send("/api/movements", """
                    {"type":"OPENING_BALANCE","id":"%s","date":"%s","description":"Algo",
                     "account":"%s","equity":"%s","amountInCents":1000}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, equity));

            assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("al anular")
    class WhenCancelling {

        @Test
        @DisplayName("H3 · responde 201 con el id del movimiento de la ruta")
        void itAnswersWithThePathId() throws Exception {
            UUID movement = UUID.randomUUID();
            send("/api/movements", expense(movement, 2_500_000));

            MvcResult cancelled = send("/api/movements/" + movement + "/cancellations",
                    """
                    {"reason":"Lo registré dos veces"}""");

            assertThat(cancelled.getResponse().getStatus()).isEqualTo(201);
            JsonNode body = read(cancelled);
            // Cancellation no trae el id, asi que esto comprueba que el
            // controlador lo pone desde la ruta.
            assertThat(body.get("movement").asString()).isEqualTo(movement.toString());
            assertThat(body.get("reason").asString()).isEqualTo("Lo registré dos veces");
            assertThat(body.get("source").asString()).isEqualTo("APP");
            assertThat(body.get("at").asString()).endsWith("Z");
        }

        @Test
        @DisplayName("H3 · con el mismo motivo responde 200")
        void thesameReasonIs200() throws Exception {
            UUID movement = UUID.randomUUID();
            send("/api/movements", expense(movement, 2_500_000));
            String path = "/api/movements/" + movement + "/cancellations";
            String reason = """
                    {"reason":"Lo registré dos veces"}""";

            MvcResult first = send(path, reason);
            MvcResult again = send(path, reason);

            assertThat(first.getResponse().getStatus()).isEqualTo(201);
            assertThat(again.getResponse().getStatus()).isEqualTo(200);
            assertThat(again.getResponse().getContentAsString())
                    .isEqualTo(first.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("H3 · con otro motivo responde 409")
        void anotherReasonIs409() throws Exception {
            UUID movement = UUID.randomUUID();
            send("/api/movements", expense(movement, 2_500_000));
            String path = "/api/movements/" + movement + "/cancellations";

            send(path, """
                    {"reason":"Lo registré dos veces"}""");
            MvcResult conflict = send(path, """
                    {"reason":"Otra razón distinta"}""");

            assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
            assertThat(read(conflict).get("type").asString())
                    .isEqualTo("urn:finance:error:already-cancelled");
        }

        @Test
        @DisplayName("H3 · un movimiento que no existe responde 404")
        void anUnknownMovementIs404() throws Exception {
            MvcResult missing = send(
                    "/api/movements/" + UUID.randomUUID() + "/cancellations",
                    """
                    {"reason":"No existe"}""");

            assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("al corregir")
    class WhenAmending {

        @Test
        @DisplayName("H3 · la corrección apunta al original y responde 201")
        void theCorrectionPointsAtTheOriginal() throws Exception {
            UUID original = UUID.randomUUID();
            send("/api/movements", expense(original, 2_500_000));
            UUID correction = UUID.randomUUID();

            MvcResult amended = send("/api/movements/" + original + "/amendments",
                    amendment(correction, 3_000_000));

            assertThat(amended.getResponse().getStatus()).isEqualTo(201);
            JsonNode body = read(amended);
            assertThat(body.get("id").asString()).isEqualTo(correction.toString());
            assertThat(body.get("replaces").asString()).isEqualTo(original.toString());
        }

        @Test
        @DisplayName("H3 · repetirla igual responde 200 con el mismo cuerpo")
        void replayingIt() throws Exception {
            UUID original = UUID.randomUUID();
            send("/api/movements", expense(original, 2_500_000));
            UUID correction = UUID.randomUUID();
            String path = "/api/movements/" + original + "/amendments";

            MvcResult first = send(path, amendment(correction, 3_000_000));
            MvcResult again = send(path, amendment(correction, 3_000_000));

            assertThat(first.getResponse().getStatus()).isEqualTo(201);
            assertThat(again.getResponse().getStatus()).isEqualTo(200);
            assertThat(again.getResponse().getContentAsString())
                    .isEqualTo(first.getResponse().getContentAsString());
        }

        @Test
        @DisplayName("H3 · un id de corrección ya usado responde 409")
        void aReusedCorrectionIdIs409() throws Exception {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            send("/api/movements", expense(first, 2_500_000));
            send("/api/movements", expense(second, 1_000_000));
            UUID correction = UUID.randomUUID();

            send("/api/movements/" + first + "/amendments", amendment(correction, 3_000_000));
            MvcResult conflict = send("/api/movements/" + second + "/amendments",
                    amendment(correction, 3_000_000));

            assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        }

        @Test
        @DisplayName("H3 · la corrección anidada también se valida")
        void theNestedCorrectionIsValidated() throws Exception {
            // Sin el @Valid en AmendMovementRequest.correction, esto pasaria.
            UUID original = UUID.randomUUID();
            send("/api/movements", expense(original, 2_500_000));

            MvcResult refused = send("/api/movements/" + original + "/amendments", """
                    {"correction":{"type":"EXPENSE","id":"%s","date":"%s","description":null,
                     "paidFrom":"%s","category":"%s","amountInCents":-5}}"""
                    .formatted(UUID.randomUUID(), TODAY, bank, food));

            assertThat(refused.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(refused).get("type").asString())
                    .isEqualTo("urn:finance:error:invalid-request");
        }

        private String amendment(UUID correction, long cents) {
            return """
                    {"correction":{"type":"EXPENSE","id":"%s","date":"%s",
                     "description":"Almuerzo corregido","paidFrom":"%s","category":"%s",
                     "amountInCents":%d}}"""
                    .formatted(correction, TODAY, bank, food, cents);
        }
    }

    private String expense(UUID id, long cents) {
        return """
                {"type":"EXPENSE","id":"%s","date":"%s","description":"Almuerzo",
                 "paidFrom":"%s","category":"%s","amountInCents":%d}"""
                .formatted(id, TODAY, bank, food, cents);
    }

    private UUID openAccount(String type, String name) throws Exception {
        UUID id = UUID.randomUUID();
        MvcResult opened = send("/api/accounts",
                """
                {"id":"%s","type":"%s","name":"%s"}""".formatted(id, type, name));

        assertThat(opened.getResponse().getStatus())
                .as("abriendo %s: %s", name, opened.getResponse().getContentAsString())
                .isEqualTo(201);
        return id;
    }

    private MvcResult send(String path, String body) throws Exception {
        return mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}

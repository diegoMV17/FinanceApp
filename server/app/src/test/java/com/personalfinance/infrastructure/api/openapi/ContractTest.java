package com.personalfinance.infrastructure.api.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.personalfinance.infrastructure.PostgresIntegrationTest;
import com.personalfinance.infrastructure.api.dto.request.MovementRequest;
import com.personalfinance.infrastructure.api.error.ApiError;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * H21–H25. El contrato entre el servidor y la app, escrito desde el código.
 *
 * <p>Este es el único sitio donde springdoc se enciende: pide la
 * especificación, la escribe en {@code ../contract/openapi.json} y falla si el
 * archivo guardado no coincide con lo que sale del código. Cambiar la API sin
 * actualizar el contrato rompe el build, y un PR que cambia la API muestra el
 * cambio en ese archivo, que es el que lee {@code mobile/} para generar su
 * cliente.
 *
 * <p>Su contexto es distinto del de los demás tests (la ruta encendida) y se
 * cierra al terminar: así no queda vivo un segundo contexto con su propio
 * Postgres mientras corre el resto.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("El contrato OpenAPI")
class ContractTest extends PostgresIntegrationTest {

    /** Relativa a {@code server/app}, que es donde Gradle corre los tests. */
    private static final Path CONTRACT = Path.of("../../contract/openapi.json");

    private static final String BEARER = "Bearer " + TEST_API_TOKEN;

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("H21 · el contrato guardado es el que sale del código")
    void theSavedContractMatchesTheCode() throws Exception {
        String current = canonical(specification());
        String saved = Files.exists(CONTRACT)
                ? Files.readString(CONTRACT, StandardCharsets.UTF_8).replace("\r\n", "\n")
                : null;

        if (!current.equals(saved)) {
            // Se reescribe y se falla: en la máquina de quien cambió la API el
            // archivo queda al día para revisar el diff y comitearlo; en CI el
            // build se pone rojo, que es lo que importa.
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, current, StandardCharsets.UTF_8);
            fail("contrato desactualizado: se reescribió contract/openapi.json; "
                    + "revisa el diff y commitéalo junto con el cambio de la API");
        }
    }

    @Test
    @DisplayName("H22 · el token es el esquema de seguridad de todo, y no hay servidor escrito")
    void bearerEverywhereAndNoServer() throws Exception {
        JsonNode spec = json.readTree(specification());

        JsonNode bearer = spec.path("components").path("securitySchemes").path("bearer");
        assertThat(bearer.path("type").asString()).isEqualTo("http");
        assertThat(bearer.path("scheme").asString()).isEqualTo("bearer");
        assertThat(spec.path("security").toString()).isEqualTo("[{\"bearer\":[]}]");

        // La dirección del servidor es privada (el nombre de la tailnet) y la
        // escribe el dueño en la app; el contrato no la sabe ni la inventa.
        assertThat(spec.has("servers")).as("sin servers").isFalse();
        String text = spec.toString();
        assertThat(text).doesNotContain("http://").doesNotContain("https://");
        // Nunca ejemplos: un ejemplo tienta a copiar datos de verdad.
        assertThat(text).doesNotContain("\"example\"").doesNotContain("\"examples\"");
    }

    @Test
    @DisplayName("H23 · los errores son la lista cerrada de ApiError, con su estado")
    void errorsAreTheClosedList() throws Exception {
        JsonNode spec = json.readTree(specification());
        JsonNode problem = spec.path("components").path("schemas").path("Problem");

        List<String> declared = new ArrayList<>();
        problem.path("properties").path("type").path("enum")
                .values().forEach(node -> declared.add(node.asString()));
        assertThat(declared).containsExactly(
                Arrays.stream(ApiError.values()).map(ApiError::type).toArray(String[]::new));

        Map<String, Integer> statuses = new LinkedHashMap<>();
        problem.path("x-status-by-type").properties()
                .forEach(entry -> statuses.put(entry.getKey(), entry.getValue().asInt()));
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (ApiError error : ApiError.values()) {
            expected.put(error.type(), error.status().value());
        }
        assertThat(statuses).isEqualTo(expected);

        // Y toda operación dice que su error llega en ese formato.
        assertThat(spec.path("paths").properties()).isNotEmpty();
        spec.path("paths").properties().forEach(path ->
                path.getValue().properties().forEach(operation -> {
                    JsonNode fallback = operation.getValue().path("responses").path("default");
                    assertThat(fallback.path("content").path("application/problem+json")
                                    .path("schema").path("$ref").asString())
                            .as("%s %s", operation.getKey(), path.getKey())
                            .isEqualTo("#/components/schemas/Problem");
                }));
    }

    @Test
    @DisplayName("H24 · MovementRequest es un oneOf con el discriminador explícito")
    void movementRequestHasAnExplicitDiscriminator() throws Exception {
        JsonNode schemas = json.readTree(specification()).path("components").path("schemas");
        JsonNode movement = schemas.path("MovementRequest");

        JsonSubTypes.Type[] subtypes =
                MovementRequest.class.getAnnotation(JsonSubTypes.class).value();
        Map<String, String> expectedMapping = new LinkedHashMap<>();
        List<String> expectedRefs = new ArrayList<>();
        for (JsonSubTypes.Type subtype : subtypes) {
            String ref = "#/components/schemas/" + subtype.value().getSimpleName();
            expectedMapping.put(subtype.name(), ref);
            expectedRefs.add(ref);
        }

        List<String> refs = new ArrayList<>();
        movement.path("oneOf").values().forEach(node -> refs.add(node.path("$ref").asString()));
        assertThat(refs).containsExactlyElementsOf(expectedRefs);

        assertThat(movement.path("discriminator").path("propertyName").asString())
                .isEqualTo("type");
        Map<String, String> mapping = new LinkedHashMap<>();
        movement.path("discriminator").path("mapping").properties()
                .forEach(entry -> mapping.put(entry.getKey(), entry.getValue().asString()));
        assertThat(mapping).isEqualTo(expectedMapping);

        for (JsonSubTypes.Type subtype : subtypes) {
            String name = subtype.value().getSimpleName();
            JsonNode variant = schemas.path(name);
            assertThat(variant.has("allOf"))
                    .as("%s no se compone con el padre: la referencia circular rompe a "
                            + "los generadores", name)
                    .isFalse();
            assertThat(variant.path("properties").path("type").path("enum").toString())
                    .as("%s declara su type con un solo valor", name)
                    .isEqualTo("[\"" + subtype.name() + "\"]");
            List<String> required = new ArrayList<>();
            variant.path("required").values().forEach(node -> required.add(node.asString()));
            assertThat(required).as("%s exige su type", name).contains("type");
        }

        // La corrección lleva un movimiento entero dentro: el mismo oneOf.
        assertThat(schemas.path("AmendMovementRequest").path("properties").path("correction")
                        .path("$ref").asString())
                .isEqualTo("#/components/schemas/MovementRequest");
    }

    @Test
    @DisplayName("H26 · toda escritura dice 201 (aplicado) y 200 (ya estaba), con el mismo cuerpo")
    void everyWriteAnswersAppliedOrAlreadyApplied() throws Exception {
        JsonNode paths = json.readTree(specification()).path("paths");
        List<String> writes = new ArrayList<>();
        paths.properties().forEach(path -> {
            JsonNode post = path.getValue().path("post");
            if (post.isMissingNode()) {
                return;
            }
            writes.add(path.getKey());
            JsonNode responses = post.path("responses");
            assertThat(responses.path("201").path("content"))
                    .as("POST %s responde 201", path.getKey())
                    .isEqualTo(responses.path("200").path("content"))
                    .isNotEmpty();
        });
        // Las seis del libro, las cuatro de personas y la de presupuestos.
        assertThat(writes).hasSize(11);
    }

    @Test
    @DisplayName("H26 · cada operación tiene un id estable, sin sufijos de desempate")
    void operationIdsAreStable() throws Exception {
        // springdoc desempata repetidos (open, open_1) según el orden en que
        // encuentra los controladores: el contrato cambiaría sin que cambiara
        // la API, y el cliente generado con él.
        List<String> ids = new ArrayList<>();
        json.readTree(specification()).path("paths").properties().forEach(path ->
                path.getValue().properties().forEach(operation ->
                        ids.add(operation.getValue().path("operationId").asString())));
        assertThat(ids).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).matches("[a-z][A-Za-z]+"))
                .contains("accountsOpen", "peopleOpen", "movementsRecord", "bookSummary");
    }

    @Test
    @DisplayName("H27 · un número primitivo siempre viaja: es obligatorio en el contrato")
    void primitivesAreRequired() throws Exception {
        JsonNode schemas = json.readTree(specification()).path("components").path("schemas");
        // Un long de Java no puede faltar: si no llega, Jackson pone 0 y la
        // validación lo rechaza; en una respuesta, siempre se escribe. Sin
        // esto el cliente lo tendría como opcional y la app aprendería a
        // completar un monto que falta con un cero.
        assertThat(requiredOf(schemas.path("ExpenseRequest"))).contains("amountInCents");
        assertThat(requiredOf(schemas.path("SetBudgetRequest"))).contains("limitInCents");
        assertThat(requiredOf(schemas.path("SummaryResponse")))
                .contains("expensesThatMonthInCents", "moneyReallyMineInCents");
        assertThat(requiredOf(schemas.path("BudgetProgressResponse")))
                .contains("exceeded", "nearTheLimit", "spentInCents");
    }

    @Test
    @DisplayName("H25 · aun encendida, la especificación no se lee sin token")
    void evenWhenOnItNeedsTheToken() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    private static List<String> requiredOf(JsonNode schema) {
        List<String> required = new ArrayList<>();
        schema.path("required").values().forEach(node -> required.add(node.asString()));
        return required;
    }

    private String specification() throws Exception {
        return mvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Una sola forma de escribir el mismo documento: claves ordenadas, con
     * sangría y LF al final. Sin esto, el orden en que springdoc recorre los
     * controladores cambiaría el archivo sin que la API cambiara.
     */
    private String canonical(String specification) {
        JsonMapper sorted = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
        Map<String, Object> tree = json.readValue(specification, new TypeReference<>() {});
        return sorted.writeValueAsString(tree).replace("\r\n", "\n") + "\n";
    }
}

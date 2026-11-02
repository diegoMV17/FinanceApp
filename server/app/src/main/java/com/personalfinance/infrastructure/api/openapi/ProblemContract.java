package com.personalfinance.infrastructure.api.openapi;

import com.personalfinance.infrastructure.api.error.ApiError;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Los errores de la API en el contrato: la lista cerrada de {@link ApiError},
 * con el estado de cada uno.
 *
 * <p>Todo sale de {@code ApiError.values()} y nada se repite aquí: un código
 * nuevo entra al contrato solo, y {@code ContractTest} obliga a comitearlo. El
 * {@code type} es un {@code enum} porque es el código estable con el que el
 * cliente decide qué decirle al usuario; un {@code string} libre le dejaría
 * creer que puede llegar cualquier cosa.
 *
 * <p>Cada operación recibe una respuesta {@code default} con este esquema en
 * vez de una lista por código: qué errores puede dar cada ruta no lo sabe nadie
 * con certeza (el brazo de recogida de {@code LedgerException} existe justo por
 * eso), y una lista incompleta sería una promesa falsa. Lo cierto es que todo
 * error llega con esta forma.
 */
final class ProblemContract implements OpenApiCustomizer {

    static final String PROBLEM = "Problem";
    static final String PROBLEM_JSON = "application/problem+json";

    @Override
    public void customise(OpenAPI openApi) {
        openApi.getComponents().addSchemas(PROBLEM, problem());

        ApiResponse anyError = new ApiResponse()
                .description("Cualquier error, como RFC 9457. El type es uno de la lista cerrada.")
                .content(new Content().addMediaType(PROBLEM_JSON,
                        new MediaType().schema(new Schema<>().$ref(PROBLEM))));
        openApi.getPaths().values().forEach(path ->
                path.readOperations().forEach(operation ->
                        operation.getResponses().setDefault(anyError)));
    }

    private static Schema<?> problem() {
        List<String> types = Arrays.stream(ApiError.values()).map(ApiError::type).toList();
        Map<String, Integer> statusByType = new LinkedHashMap<>();
        for (ApiError error : ApiError.values()) {
            statusByType.put(error.type(), error.status().value());
        }

        Schema<?> problem = new ObjectSchema()
                .description("Un error de la API (RFC 9457). El detail es un texto fijo por "
                        + "type: nunca trae datos del libro.")
                .addProperty("type", new StringSchema()._enum(types))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("errorId", new StringSchema()
                        .description("Solo en un error inesperado (500): con qué preguntar "
                                + "en el log del servidor."))
                .required(List.of("detail", "status", "title", "type"));
        problem.addExtension("x-status-by-type", statusByType);
        return problem;
    }
}

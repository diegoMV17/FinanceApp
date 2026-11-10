package com.personalfinance.infrastructure.api.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * En una respuesta, todo campo viaja, salvo el que lleva {@code @Nullable}.
 *
 * <p>springdoc no sabe qué referencias nunca son nulas y las dejaba todas
 * opcionales: en el cliente generado, el {@code id} y el {@code name} de una
 * cuenta eran nullables, y la app tendría que inventar qué pintar cuando
 * faltan. Lo que de verdad puede faltar —un archivado que no ocurrió, una
 * anulación que no hubo, un reemplazo que no existe— lo dice el record con
 * {@code @Nullable} en su componente: la marca vive pegada al dato, y esta
 * regla solo la lee.
 *
 * <p>Solo las respuestas. En una petición, que un campo falte lo decide Bean
 * Validation y lo dice el contrato por su cuenta ({@code @NotNull}).
 */
final class RequiredResponseFieldsContract implements OpenApiCustomizer {

    private static final String RESPONSES = "com.personalfinance.infrastructure.api.dto.response";

    @Override
    @SuppressWarnings("rawtypes")
    public void customise(OpenAPI openApi) {
        for (Map.Entry<String, Schema> schema : openApi.getComponents().getSchemas().entrySet()) {
            Class<?> record = responseNamed(schema.getKey());
            if (record != null) {
                requireAllButNullable(schema.getValue(), record);
            }
        }
    }

    private static void requireAllButNullable(Schema<?> schema, Class<?> record) {
        Set<String> required = new TreeSet<>();
        if (schema.getRequired() != null) {
            required.addAll(schema.getRequired());
        }
        for (RecordComponent component : record.getRecordComponents()) {
            if (!component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                required.add(component.getName());
            }
        }
        schema.setRequired(List.copyOf(required));
    }

    private static @Nullable Class<?> responseNamed(String simpleName) {
        try {
            Class<?> candidate = Class.forName(RESPONSES + "." + simpleName);
            return candidate.isRecord() ? candidate : null;
        } catch (ClassNotFoundException notAResponse) {
            // Una petición o un esquema propio del contrato (Problem,
            // MovementRequest, los enums).
            return null;
        }
    }
}

package com.personalfinance.infrastructure.api.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Un campo primitivo de un DTO es obligatorio en el contrato.
 *
 * <p>springdoc solo marca como obligatorio lo que lleva {@code @NotNull}, y un
 * {@code long} no lo lleva porque no puede ser nulo. Pero tampoco puede
 * faltar: en una petición, si no llega, Jackson pone 0 y la validación lo
 * rechaza; en una respuesta, siempre se escribe. Sin esta regla el cliente
 * generado tendría {@code amountInCents} como opcional, y la app aprendería a
 * completar con un cero un monto que falta, que en una app de plata es justo
 * lo que no se hace.
 *
 * <p>La regla mira los records de verdad, por reflexión, y no una lista de
 * nombres de campos: un DTO nuevo la cumple solo.
 */
final class RequiredPrimitivesContract implements OpenApiCustomizer {

    private static final List<String> DTO_PACKAGES = List.of(
            "com.personalfinance.infrastructure.api.dto.request",
            "com.personalfinance.infrastructure.api.dto.response");

    @Override
    @SuppressWarnings("rawtypes")
    public void customise(OpenAPI openApi) {
        for (Map.Entry<String, Schema> schema : openApi.getComponents().getSchemas().entrySet()) {
            recordNamed(schema.getKey()).ifPresent(record -> requirePrimitives(schema.getValue(), record));
        }
    }

    private static void requirePrimitives(Schema<?> schema, Class<?> record) {
        Set<String> required = new TreeSet<>();
        if (schema.getRequired() != null) {
            required.addAll(schema.getRequired());
        }
        for (RecordComponent component : record.getRecordComponents()) {
            if (component.getType().isPrimitive()) {
                required.add(component.getName());
            }
        }
        if (!required.isEmpty()) {
            schema.setRequired(List.copyOf(required));
        }
    }

    private static Optional<Class<?>> recordNamed(String simpleName) {
        for (String pkg : DTO_PACKAGES) {
            try {
                Class<?> candidate = Class.forName(pkg + "." + simpleName);
                if (candidate.isRecord()) {
                    return Optional.of(candidate);
                }
            } catch (ClassNotFoundException notHere) {
                // Puede estar en el otro paquete, o ser un esquema propio del
                // contrato (Problem, MovementRequest).
            }
        }
        return Optional.empty();
    }
}

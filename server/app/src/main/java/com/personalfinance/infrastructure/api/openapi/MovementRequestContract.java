package com.personalfinance.infrastructure.api.openapi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.personalfinance.infrastructure.api.dto.request.MovementRequest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * {@link MovementRequest} en el contrato: un {@code oneOf} con el discriminador
 * {@code type} escrito, no deducido.
 *
 * <p>Lo que springdoc saca solo de {@code @JsonTypeInfo} es la forma de la
 * herencia de Java: un padre con el discriminador y once hijos que se componen
 * con él por {@code allOf}, más un {@code oneOf} suelto repetido donde se usa.
 * Es correcto, y es el caso que peor manejan los generadores: la referencia del
 * hijo al padre y del padre a los hijos es circular, y el {@code type} no
 * aparece en ningún hijo, así que un cliente generado no sabe escribirlo.
 *
 * <p>Esto lo reescribe en la forma plana: {@code MovementRequest} es un
 * {@code oneOf} de los once con {@code discriminator.mapping}; cada variante
 * declara {@code type} obligatorio con su único valor; y donde springdoc dejó
 * el {@code oneOf} suelto (el cuerpo de {@code POST /api/movements} y la
 * {@code correction} de una corrección) queda una referencia a
 * {@code MovementRequest}.
 *
 * <p>La lista sale de {@code @JsonSubTypes}, la misma que usa Jackson para
 * leer el cuerpo y que {@code MovementRequestCoverageTest} compara con los
 * {@code permits}: el contrato no tiene su propia copia de los once.
 */
final class MovementRequestContract implements OpenApiCustomizer {

    private static final String UNION = MovementRequest.class.getSimpleName();

    @Override
    public void customise(OpenAPI openApi) {
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        String discriminator = MovementRequest.class.getAnnotation(JsonTypeInfo.class).property();
        JsonSubTypes.Type[] subtypes = MovementRequest.class.getAnnotation(JsonSubTypes.class).value();

        ComposedSchema union = new ComposedSchema();
        Map<String, String> mapping = new LinkedHashMap<>();
        for (JsonSubTypes.Type subtype : subtypes) {
            String name = subtype.value().getSimpleName();
            union.addOneOfItem(new Schema<>().$ref(name));
            mapping.put(subtype.name(), ref(name));
            schemas.put(name, flattened(schemas.get(name), discriminator, subtype.name()));
        }
        union.discriminator(new Discriminator().propertyName(discriminator).mapping(mapping));
        union.description("Un movimiento, uno de once tipos según el campo " + discriminator + ".");
        schemas.put(UNION, union);

        Set<String> variants = Stream.of(subtypes)
                .map(subtype -> ref(subtype.value().getSimpleName()))
                .collect(Collectors.toSet());
        schemas.values().forEach(schema -> pointPropertiesAtTheUnion(schema, variants));
        openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                operation.getRequestBody().getContent().values().forEach(media -> {
                    if (isTheUnion(media.getSchema(), variants)) {
                        media.setSchema(new Schema<>().$ref(UNION));
                    }
                });
            }
        }));
    }

    /**
     * La variante sin el {@code allOf} hacia el padre: sus propias propiedades,
     * más el discriminador con su único valor, obligatorio.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema<?> flattened(Schema<?> variant, String discriminator, String value) {
        Schema<?> flat = new ObjectSchema();
        flat.addProperty(discriminator, new StringSchema()._enum(List.of(value)));
        if (variant.getAllOf() != null) {
            for (Schema part : variant.getAllOf()) {
                if (part.get$ref() == null && part.getProperties() != null) {
                    part.getProperties().forEach((name, property) ->
                            flat.addProperty((String) name, (Schema) property));
                }
            }
        }
        if (variant.getProperties() != null) {
            variant.getProperties().forEach((name, property) -> {
                if (!name.equals(discriminator)) {
                    flat.addProperty(name, property);
                }
            });
        }
        Set<String> required = new TreeSet<>();
        required.add(discriminator);
        if (variant.getRequired() != null) {
            required.addAll(variant.getRequired());
        }
        flat.setRequired(List.copyOf(required));
        flat.setDescription(variant.getDescription());
        return flat;
    }

    @SuppressWarnings("rawtypes")
    private static void pointPropertiesAtTheUnion(Schema<?> schema, Set<String> variants) {
        if (schema.getProperties() == null) {
            return;
        }
        for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
            if (isTheUnion(property.getValue(), variants)) {
                property.setValue(new Schema<>().$ref(UNION));
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static boolean isTheUnion(Schema<?> schema, Set<String> variants) {
        if (schema == null || schema.getOneOf() == null) {
            return false;
        }
        Set<String> refs = new TreeSet<>();
        for (Schema option : schema.getOneOf()) {
            refs.add(option.get$ref());
        }
        return refs.equals(variants);
    }

    private static String ref(String name) {
        return "#/components/schemas/" + name;
    }
}

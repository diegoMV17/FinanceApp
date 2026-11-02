package com.personalfinance.infrastructure.api.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * El nombre y las respuestas de cada operación.
 *
 * <p><strong>Nombres estables.</strong> springdoc usa el nombre del método como
 * {@code operationId} y desempata los repetidos ({@code open}, {@code open_1})
 * según el orden en que encuentra los controladores. Ese orden no lo fija nadie:
 * el contrato cambiaría sin que cambiara la API, y el cliente generado con él.
 * Aquí el id es el del controlador más el del método ({@code accountsOpen},
 * {@code peopleOpen}), único por construcción, y la etiqueta es la del
 * controlador sin el sufijo, que es lo que el cliente generado usa para
 * agruparlas.
 *
 * <p><strong>201 y 200 en toda escritura.</strong> Toda escritura responde por
 * {@code Answers}: 201 si escribió ahora y 200 si ya estaba aplicada, con el
 * mismo cuerpo. springdoc solo ve el tipo de retorno y anuncia un 200, que es
 * media verdad; la cola de pendientes de la app trata los dos como éxito y
 * necesita saber que existen los dos.
 */
final class OperationsContract implements OperationCustomizer {

    private static final String CONTROLLER = "Controller";

    @Override
    public Operation customize(Operation operation, HandlerMethod handler) {
        String controller = handler.getBeanType().getSimpleName();
        if (controller.endsWith(CONTROLLER)) {
            controller = controller.substring(0, controller.length() - CONTROLLER.length());
        }
        String tag = Character.toLowerCase(controller.charAt(0)) + controller.substring(1);
        String method = handler.getMethod().getName();
        operation.setTags(List.of(tag));
        operation.setOperationId(tag + Character.toUpperCase(method.charAt(0)) + method.substring(1));

        if (handler.hasMethodAnnotation(PostMapping.class)) {
            ApiResponses responses = operation.getResponses();
            ApiResponse ok = responses.get("200");
            if (ok != null) {
                responses.addApiResponse("201", new ApiResponse()
                        .description("Aplicado: se escribió ahora.")
                        .content(ok.getContent()));
                ok.setDescription("Ya estaba aplicado: el mismo cuerpo, sin escribir nada.");
            }
        }
        return operation;
    }
}

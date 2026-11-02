package com.personalfinance.infrastructure.api.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lo que la especificación dice de la API entera.
 *
 * <p>Todo esto son beans inertes en el servidor: springdoc está apagado en
 * {@code application.yml} y solo lo enciende {@code ContractTest}, que escribe
 * {@code ../contract/openapi.json}. Aquí no hay nada que se publique.
 *
 * <p>Sin {@code servers}, a propósito. springdoc escribiría la dirección con la
 * que se le preguntó ({@code http://localhost} en el test), y la de verdad es
 * el nombre de la tailnet, que es privado y lo escribe el dueño en la app. Un
 * contrato que no sabe dónde vive la API tampoco puede filtrarlo, y además no
 * cambia según la máquina donde se generó.
 */
@Configuration
class OpenApiConfiguration {

    static final String BEARER = "bearer";

    @Bean
    OpenAPI financeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinanceApp API")
                        .version("1")
                        .description("El libro de finanzas personales, en partida doble. "
                                + "Toda ruta exige el token."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * springdoc agrega un {@code servers} con la dirección de la petición solo
     * si esa dirección no es nula; borrarlo con un customizer no basta, porque
     * se calcula después. Una dirección nula es la forma de que no lo escriba.
     */
    @Bean
    ServerBaseUrlCustomizer withoutServers() {
        return (serverBaseUrl, request) -> null;
    }

    @Bean
    OpenApiCustomizer problemContract() {
        return new ProblemContract();
    }

    @Bean
    OpenApiCustomizer movementRequestContract() {
        return new MovementRequestContract();
    }

    @Bean
    OpenApiCustomizer requiredPrimitivesContract() {
        return new RequiredPrimitivesContract();
    }

    @Bean
    OperationsContract operationsContract() {
        return new OperationsContract();
    }
}

package com.personalfinance.infrastructure.api.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.personalfinance.infrastructure.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springdoc.webmvc.api.OpenApiResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S20. Con la configuración de producción, springdoc no publica nada.
 *
 * <p>Una descripción completa de la API es una herramienta de administración:
 * le dice a quien la lea cada ruta, cada campo y cada código de error. La
 * especificación se genera en un test ({@code ContractTest}) y se guarda como
 * archivo; en el servidor, {@code /v3/api-docs} no existe.
 *
 * <p>Se pide <strong>con token</strong> a propósito. Sin token cualquier ruta
 * responde 401, exista o no, así que un 401 no diría nada de springdoc: diría
 * que la puerta funciona, que ya lo prueba {@code ApiSecurityWiringTest}. Con
 * token, una ruta encendida respondería 200 y una apagada 404.
 */
@AutoConfigureMockMvc
@DisplayName("S20 · las rutas de springdoc")
class ApiDocsAreOffTest extends PostgresIntegrationTest {

    private static final String BEARER = "Bearer " + TEST_API_TOKEN;

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    @ParameterizedTest(name = "S20 · {0} no responde, ni siquiera con token")
    @ValueSource(strings = {
        "/v3/api-docs",
        "/v3/api-docs.yaml",
        "/v3/api-docs/swagger-config",
        "/swagger-ui.html",
        "/swagger-ui/index.html"
    })
    void isNotPublishedEvenWithAToken(String path) throws Exception {
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "S20 · {0} sin token: 401, como todo")
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui.html"})
    void withoutATokenIsTheUsual401(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("S20 · springdoc no levanta su controlador")
    void theSpecificationEndpointIsNotEvenABean() {
        // La prueba estructural: que el 404 no dependa de cómo se resuelve hoy
        // una ruta, sino de que el controlador que la sirve no existe.
        assertThat(context.getBeanNamesForType(OpenApiResource.class))
                .as("springdoc.api-docs.enabled tiene que estar en false en application.yml")
                .isEmpty();
    }
}

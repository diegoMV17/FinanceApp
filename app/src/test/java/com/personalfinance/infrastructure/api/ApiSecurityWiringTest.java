package com.personalfinance.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.personalfinance.infrastructure.PostgresIntegrationTest;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S10. Que la puerta esté puesta de verdad.
 *
 * <p>Los tests de {@code ApiTokenFilterTest} prueban que el filtro decide bien;
 * este prueba algo distinto y que ningún test unitario puede probar: que el
 * filtro está <em>conectado</em>, que la cadena de Spring Security existe, y que
 * una ruta que nadie escribió tampoco se puede visitar sin token.
 *
 * <p>Lo importante es que las rutas que se piden aquí <strong>no existen</strong>.
 * Si lo que protege la API fuera una lista de rutas, estas se colarían. Lo que
 * la protege es {@code anyRequest().authenticated()}, así que una ruta
 * inexistente responde 401 y no 404: sin token no se puede ni averiguar qué hay.
 */
@AutoConfigureMockMvc
@DisplayName("La puerta de la API")
class ApiSecurityWiringTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    @Nested
    @DisplayName("al arrancar")
    class WhenStarting {

        @Test
        @DisplayName("S10 · la cadena de seguridad existe")
        void theSecurityChainExists() {
            assertThat(context.getBeansOfType(SecurityFilterChain.class))
                    .as("sin cadena, Spring Security no filtra nada")
                    .hasSize(1);
        }

        @Test
        @DisplayName("S10 · ningún filtro propio queda registrado dos veces")
        void theTokenFilterIsNotRegisteredTwice() {
            // Un bean de tipo Filter lo registra Boot también como filtro del
            // contenedor, por su cuenta y fuera de la cadena de Spring
            // Security. Esa copia de fuera pondría el contexto de seguridad sin
            // que nadie lo limpie, y eso se filtra al siguiente que use el
            // hilo. Que no haya ningún bean de ese tipo es la forma
            // estructural de evitarlo, en vez de confiar en qué orden de
            // registro le toca.
            assertThat(context.getBeansOfType(Filter.class).values())
                    .as("ningún filtro de la API es un bean suelto; "
                            + "el del token se construye dentro de la cadena y el "
                            + "del tamaño va envuelto en un FilterRegistrationBean")
                    .noneMatch(filter -> filter.getClass().getName()
                            .startsWith("com.personalfinance.infrastructure.api"));
        }

        @Test
        @DisplayName("S10 · y no hay ningún usuario en memoria")
        void thereIsNoInMemoryUser() {
            // Si UserDetailsServiceAutoConfiguration se cuela, Boot crea un
            // usuario y escribe su clave generada en el log al arrancar. Una
            // clave en el log es un secreto en el log, y el CLAUDE.md dice que
            // eso no pasa. La exclusión está en application.yml; esto es lo que
            // avisa si alguien la quita.
            assertThat(context.getBeansOfType(UserDetailsService.class))
                    .as("nadie debería haber creado un usuario que no pedimos")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("sin token")
    class WithoutAToken {

        @Test
        @DisplayName("S10 · una ruta que no existe responde 401, no 404")
        void anUnknownRouteIsStill401() throws Exception {
            MvcResult result = mvc.perform(get("/api/no-existe-esta-ruta"))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("deny by default: ni se averigua qué hay detrás")
                    .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("S10 · y la raíz tampoco")
        void andNeitherDoesTheRoot() throws Exception {
            assertThat(mvc.perform(get("/")).andReturn().getResponse().getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(mvc.perform(get("/actuator/health"))
                            .andReturn().getResponse().getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(mvc.perform(post("/cualquier-cosa"))
                            .andReturn().getResponse().getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("S10 · el 401 es problem+json y no una página de Tomcat")
        void the401IsProblemJson() throws Exception {
            MvcResult result = mvc.perform(get("/api/no-existe-esta-ruta")).andReturn();

            assertThat(result.getResponse().getContentType())
                    .as("un HTML aquí sería el formato de error del contenedor")
                    .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            String body = result.getResponse().getContentAsString();
            assertThat(body).contains("urn:finance:error:unauthorized");
            assertThat(body).doesNotContainIgnoringCase("html");
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .isEqualTo("Bearer");
            assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                    .isEqualTo("nosniff");
        }

        @Test
        @DisplayName("S10 · y no cuenta nada de lo que pasó dentro")
        void andItSaysNothingAboutTheInside() throws Exception {
            String body = mvc.perform(get("/api/no-existe-esta-ruta"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body)
                    .doesNotContain(TEST_API_TOKEN)
                    .doesNotContain("com.personalfinance")
                    .doesNotContain("org.springframework")
                    .doesNotContain("Exception")
                    .doesNotContain("no-existe-esta-ruta");
        }
    }

    @Nested
    @DisplayName("con el token")
    class WithTheToken {

        @Test
        @DisplayName("S10 · una ruta que no existe ya responde 404")
        void anUnknownRouteBecomes404() throws Exception {
            MvcResult result = mvc.perform(get("/api/no-existe-esta-ruta")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN))
                    .andReturn();

            // Que cambie de 401 a 404 es la prueba de que el token se aceptó:
            // el filtro dejó pasar y lo que falta ahora es el controlador.
            assertThat(result.getResponse().getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }
}

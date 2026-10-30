package com.personalfinance.infrastructure.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.infrastructure.api.error.ApiProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

/**
 * S6 y S7. El filtro que exige el token.
 *
 * <p>Sin Spring y sin base de datos, a propósito. Estos tests son los que de
 * verdad prueban la autenticación, y por eso tienen que correr siempre: los de
 * integración se saltan donde no hay Docker, y un build verde sin haber probado
 * la autenticación es exactamente el verde falso que no se puede permitir aquí.
 */
@DisplayName("El filtro del token")
class ApiTokenFilterTest {

    private static final String SECRET = "mQ8kR2vN7pLxT4wZ9bY6cH1jF5dS3gA0eU8iO2nK7rM";
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BLOCK_FOR = Duration.ofMinutes(1);

    private ApiTokenFilter filter;

    @BeforeEach
    void buildTheFilter() {
        ApiLimitsProperties limits = new ApiLimitsProperties(
                DataSize.ofKilobytes(64), MAX_ATTEMPTS, BLOCK_FOR, 1024);

        filter = new ApiTokenFilter(
                new TokenDigest(new ApiTokenProperties(SECRET)),
                new FailedAttempts(limits, Clock.fixed(Instant.now(), ZoneOffset.UTC)),
                new ApiProblemWriter(JsonMapper.builder().build()));
    }

    @AfterEach
    void clearTheContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("al mirar la cabecera")
    class WhenReadingTheHeader {

        @Test
        @DisplayName("S6 · con el token correcto deja pasar")
        void letsTheRightTokenThrough() throws Exception {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(requestWith("Bearer " + SECRET), response, chain);

            assertThat(chain.getRequest()).as("siguió la cadena").isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isNotNull()
                    .matches(authentication -> authentication.isAuthenticated());
        }

        @Test
        @DisplayName("S6 · el esquema no distingue mayúsculas")
        void theSchemeIsCaseInsensitive() throws Exception {
            // RFC 9110 dice que el esquema es insensible a mayúsculas, y un
            // cliente que escriba "bearer" no está haciendo nada malo.
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(requestWith("bearer " + SECRET),
                    new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("S6 · todo lo demás es 401, y el mismo 401")
        void everythingElseIsTheSame401() throws Exception {
            // La clave está en que las cuatro respuestas sean indistinguibles:
            // si "Bearer vacío" respondiera distinto de "token equivocado",
            // quien prueba sabría que acertó la forma y le faltaba el valor.
            String[] wrong = {
                null,
                "Basic " + SECRET,
                "Bearer",
                "Bearer ",
                "Bearer  " + SECRET,
                "Bearer " + SECRET.substring(0, SECRET.length() - 1) + "X",
                SECRET,
            };

            String firstBody = null;
            for (String header : wrong) {
                // Un filtro nuevo por caso, para que el limitador de intentos
                // no se meta en medio de lo que se está midiendo.
                buildTheFilter();
                MockFilterChain chain = new MockFilterChain();
                MockHttpServletResponse response = new MockHttpServletResponse();

                filter.doFilter(requestWith(header), response, chain);

                assertThat(response.getStatus())
                        .as("cabecera %s", header)
                        .isEqualTo(HttpStatus.UNAUTHORIZED.value());
                assertThat(chain.getRequest()).as("cabecera %s", header).isNull();
                assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                        .isEqualTo("Bearer");

                if (firstBody == null) {
                    firstBody = response.getContentAsString();
                } else {
                    assertThat(response.getContentAsString())
                            .as("cabecera %s responde lo mismo que las demás", header)
                            .isEqualTo(firstBody);
                }
            }
        }

        @Test
        @DisplayName("S6 · el token en la query no vale")
        void aTokenInTheQueryStringIsIgnored() throws Exception {
            // Una query acaba en los logs de acceso y en el historial del
            // navegador. El token va en la cabecera o no va.
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/api/balances");
            request.setQueryString("token=" + SECRET);
            request.setParameter("token", SECRET);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("S6 · el 401 no dice qué estaba mal")
        void the401SaysNothingUseful() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(requestWith("Bearer equivocado"), response, new MockFilterChain());

            String body = response.getContentAsString();
            assertThat(body).doesNotContain(SECRET, "equivocado", "Bearer equivocado");
            assertThat(body).contains("unauthorized");
        }
    }

    @Nested
    @DisplayName("al insistir")
    class WhenInsisting {

        @Test
        @DisplayName("S7 · tras los intentos del tope responde 429 con Retry-After")
        void blocksWithRetryAfter() throws Exception {
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                MockHttpServletResponse failed = new MockHttpServletResponse();
                filter.doFilter(requestWith("Bearer malo"), failed, new MockFilterChain());
                assertThat(failed.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            }

            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilter(requestWith("Bearer malo"), blocked, new MockFilterChain());

            assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            assertThat(blocked.getHeader(HttpHeaders.RETRY_AFTER))
                    .isNotNull()
                    .satisfies(seconds -> assertThat(Integer.parseInt(seconds)).isPositive());
        }

        @Test
        @DisplayName("S7 · y el bloqueo tampoco mira el token correcto")
        void theBlockAppliesEvenToTheRightToken() throws Exception {
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                filter.doFilter(requestWith("Bearer malo"),
                        new MockHttpServletResponse(), new MockFilterChain());
            }

            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestWith("Bearer " + SECRET), response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("S7 · entrar bien borra la cuenta de fallos")
        void gettingItRightClearsTheCount() throws Exception {
            for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
                filter.doFilter(requestWith("Bearer malo"),
                        new MockHttpServletResponse(), new MockFilterChain());
            }

            filter.doFilter(requestWith("Bearer " + SECRET),
                    new MockHttpServletResponse(), new MockFilterChain());

            MockHttpServletResponse afterwards = new MockHttpServletResponse();
            filter.doFilter(requestWith("Bearer malo"), afterwards, new MockFilterChain());

            assertThat(afterwards.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }

    @Nested
    @DisplayName("al mirar de dónde viene")
    class WhenLookingAtTheSource {

        @Test
        @DisplayName("S7 · no lee ninguna cabecera de proxy")
        void neverReadsAForwardedHeader() throws Exception {
            // Delante no hay proxy de confianza, así que estas cabeceras las
            // escribe quien llama. Si el filtro las leyera, bastaría rotar un
            // valor inventado para saltarse el limitador. El test lo garantiza
            // haciendo que leerlas sea imposible: si alguien "mejora" esto
            // mañana, revienta aquí y no en producción.
            Set<String> forbidden = Set.of(
                    "x-forwarded-for", "x-real-ip", "forwarded", "x-forwarded-host",
                    "x-client-ip", "true-client-ip");

            MockHttpServletRequest paranoid = new MockHttpServletRequest(
                    "GET", "/api/balances") {

                @Override
                public String getHeader(String name) {
                    if (forbidden.contains(name.toLowerCase(Locale.ROOT))) {
                        throw new AssertionError(
                                "el filtro leyó la cabecera de proxy " + name);
                    }
                    return super.getHeader(name);
                }
            };
            paranoid.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET);
            paranoid.setRemoteAddr("10.9.9.9");

            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(paranoid, new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("S7 · una petición sin dirección no revienta")
        void arequestWithoutAnAddressDoesNotBlowUp() throws Exception {
            // El mapa de intentos es un ConcurrentHashMap y no admite claves
            // nulas: sin un cubo de reserva, esto sería un 500 justo en el
            // filtro de autenticación, que es el peor sitio donde tener uno.
            MockHttpServletRequest nameless =
                    new MockHttpServletRequest("GET", "/api/balances");
            nameless.setRemoteAddr(null);
            nameless.addHeader(HttpHeaders.AUTHORIZATION, "Bearer malo");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(nameless, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("S7 · cada dirección tiene su propia cuenta")
        void eachAddressHasItsOwnCount() throws Exception {
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                filter.doFilter(requestFrom("10.0.0.1", "Bearer malo"),
                        new MockHttpServletResponse(), new MockFilterChain());
            }

            MockHttpServletResponse other = new MockHttpServletResponse();
            filter.doFilter(requestFrom("10.0.0.2", "Bearer malo"),
                    other, new MockFilterChain());

            assertThat(other.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }

    private static HttpServletRequest requestWith(String authorization) {
        return requestFrom("10.0.0.1", authorization);
    }

    private static HttpServletRequest requestFrom(String address, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/balances");
        request.setRemoteAddr(address);
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }
}

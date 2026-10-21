package com.personalfinance.infrastructure.api.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.personalfinance.infrastructure.api.error.ApiProblemWriter;
import com.personalfinance.infrastructure.api.security.ApiLimitsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

/**
 * S5. El tope de tamaño del cuerpo.
 *
 * <p>Tomcat no acota el cuerpo de un JSON: {@code max-http-form-post-size} solo
 * vale para formularios. Sin este filtro, cualquiera con el token — o sin él,
 * porque esto corre antes de autenticar — puede mandar un cuerpo de un giga y
 * hacer que el proceso lo lea entero.
 *
 * <p>Las dos mitades importan. El {@code Content-Length} se mira antes de leer
 * un byte, que es lo que hace el rechazo barato; y el flujo se envuelve con un
 * contador porque un cuerpo troceado llega sin {@code Content-Length} y porque
 * nada obliga a que el que llega sea verdad.
 */
@DisplayName("El tope de cuerpo")
class BodySizeLimitFilterTest {

    private static final int CAP = 64;

    private final BodySizeLimitFilter filter = new BodySizeLimitFilter(
            new ApiLimitsProperties(
                    DataSize.ofBytes(CAP), 10, Duration.ofMinutes(1), 1024),
            new ApiProblemWriter(JsonMapper.builder().build()));

    @Test
    @DisplayName("S5 · un cuerpo en el tope exacto pasa")
    void aBodyExactlyAtTheCapPasses() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestOf("a".repeat(CAP)), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(chain.getRequest()).as("la petición siguió su camino").isNotNull();
    }

    @Test
    @DisplayName("S5 · uno de un byte más se rechaza con 413")
    void oneByteOverIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestOf("a".repeat(CAP + 1)), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(response.getContentType())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("payload-too-large");
    }

    @Test
    @DisplayName("S5 · y no llega al controlador")
    void andNeverReachesTheController() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestOf("a".repeat(CAP + 1)), new MockHttpServletResponse(), chain);

        // Esto es el punto del filtro: rechazar sin que nadie haya leído el
        // cuerpo ni mirado el token.
        assertThat(chain.getRequest()).as("nadie siguió la cadena").isNull();
    }

    @Test
    @DisplayName("S5 · el cuerpo que pasa se puede leer completo y sin cambios")
    void aPassingBodyIsStillReadable() throws Exception {
        String body = "{\"description\":\"Almuerzo\"}";
        StringBuilder read = new StringBuilder();

        FilterChain reader = (request, response) -> read.append(
                new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        filter.doFilter(requestOf(body), new MockHttpServletResponse(), reader);

        // Si el envoltorio se comiera el cuerpo al contarlo, todas las
        // peticiones legítimas llegarían vacías al controlador.
        assertThat(read.toString()).isEqualTo(body);
    }

    @Test
    @DisplayName("S5 · un cuerpo troceado, sin Content-Length, también se corta")
    void aChunkedBodyIsAlsoCut() {
        // Un cuerpo troceado llega sin Content-Length: el único sitio donde se
        // puede acotar es mientras se lee.
        MockHttpServletRequest chunked = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        chunked.setMethod("POST");
        chunked.setContentType(MediaType.APPLICATION_JSON_VALUE);
        chunked.setContent("a".repeat(CAP * 4).getBytes(StandardCharsets.UTF_8));

        FilterChain reader = (request, response) -> request.getInputStream().readAllBytes();

        assertThatExceptionOfType(BodyTooLargeException.class)
                .isThrownBy(() ->
                        filter.doFilter(chunked, new MockHttpServletResponse(), reader));
    }

    @Test
    @DisplayName("S5 · un Content-Length que miente no salva al cuerpo largo")
    void aLyingContentLengthDoesNotHelp() {
        MockHttpServletRequest lying = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return 1;
            }

            @Override
            public int getContentLength() {
                return 1;
            }
        };
        lying.setMethod("POST");
        lying.setContentType(MediaType.APPLICATION_JSON_VALUE);
        lying.setContent("a".repeat(CAP * 4).getBytes(StandardCharsets.UTF_8));

        FilterChain reader = (request, response) -> request.getInputStream().readAllBytes();

        assertThatExceptionOfType(BodyTooLargeException.class)
                .isThrownBy(() ->
                        filter.doFilter(lying, new MockHttpServletResponse(), reader));
    }

    @Test
    @DisplayName("S5 · una petición sin cuerpo pasa sin ruido")
    void aBodylessRequestPasses() throws IOException, ServletException {
        MockHttpServletRequest archive = new MockHttpServletRequest("POST", "/api/accounts/x/archivals");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(archive, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static HttpServletRequest requestOf(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/movements");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}

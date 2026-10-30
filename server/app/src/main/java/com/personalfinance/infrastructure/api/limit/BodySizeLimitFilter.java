package com.personalfinance.infrastructure.api.limit;

import com.personalfinance.infrastructure.api.error.ApiError;
import com.personalfinance.infrastructure.api.error.ApiProblemWriter;
import com.personalfinance.infrastructure.api.security.ApiLimitsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Corta los cuerpos demasiado grandes antes de que alguien los lea.
 *
 * <p>Hace falta porque Tomcat no acota el cuerpo de un JSON:
 * {@code max-http-form-post-size} solo mira formularios. Sin esto, un cuerpo de
 * un giga se lee entero y lo que se agota es la memoria del proceso.
 *
 * <p>Va antes de autenticar, a propósito. Si fuera al revés, alguien sin token
 * podría hacer que el proceso lea un cuerpo enorme solo para rechazarlo
 * después, y el trabajo ya estaría hecho.
 *
 * <p>Dos mitades, y las dos son necesarias:
 *
 * <ul>
 *   <li>El {@code Content-Length}, que se mira sin leer un byte y es lo que hace
 *       el rechazo barato en el caso normal.
 *   <li>El flujo envuelto en un contador, porque un cuerpo troceado llega sin
 *       {@code Content-Length} y porque nada obliga a que el que llega sea
 *       verdad. Esta mitad es la que de verdad no se puede saltar.
 * </ul>
 */
public final class BodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ApiProblemWriter problems;

    public BodySizeLimitFilter(ApiLimitsProperties limits, ApiProblemWriter problems) {
        this.maxBytes = limits.maxBodySize().toBytes();
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (request.getContentLengthLong() > maxBytes) {
            problems.write(response, ApiError.CONTENT_TOO_LARGE);
            return;
        }
        chain.doFilter(new LimitedBodyRequest(request, maxBytes), response);
    }

    /**
     * La petición, con su cuerpo acotado mientras se lee.
     *
     * <p>Un solo contador por petición, y las dos puertas del cuerpo pasando
     * por él. Las dos cosas son la misma precaución vista de dos maneras:
     *
     * <ul>
     *   <li>Un contador nuevo en cada {@code getInputStream()} haría que el
     *       tope se salte sumando: se lee justo el tope, se vuelve a pedir el
     *       flujo, y el contador empieza de cero sobre un cuerpo que sigue
     *       donde lo dejó.
     *   <li>{@code getReader()} es la otra puerta. Hoy no la usa nadie —
     *       {@code @RequestBody} entra por el flujo —, pero un tope que solo
     *       cuenta una de las dos es un tope hasta que alguien escriba el
     *       endpoint que usa la otra, y ese día nadie se va a acordar de esto.
     * </ul>
     */
    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        private CountingStream body;
        private BufferedReader reader;

        private LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return body();
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                reader = new BufferedReader(new InputStreamReader(body(), charset()));
            }
            return reader;
        }

        private CountingStream body() throws IOException {
            if (body == null) {
                body = new CountingStream(super.getInputStream(), maxBytes);
            }
            return body;
        }

        /**
         * La codificación que diga la petición, y UTF-8 si no dice ninguna
         * que exista.
         *
         * <p>El juego de caracteres sale del {@code Content-Type}, o sea de
         * quien llama: un nombre inventado haría estallar
         * {@code Charset.forName} con una excepción sin capturar dentro del
         * filtro, y eso es un 500 donde toca un 400.
         */
        private Charset charset() {
            String encoding = getCharacterEncoding();
            if (encoding == null) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(encoding);
            } catch (IllegalArgumentException unsupported) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    /**
     * Cuenta lo que pasa y se rinde al pasarse.
     *
     * <p>No recorta ni descarta: lanza. Devolver un cuerpo truncado dejaría al
     * conversor de JSON leyendo algo que no es lo que el cliente mandó, y lo
     * que se guardaría no sería lo que llegó.
     */
    private static final class CountingStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long read;

        private CountingStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int next = delegate.read();
            if (next != -1) {
                count(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int howMany) {
            read += howMany;
            if (read > maxBytes) {
                throw new BodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}

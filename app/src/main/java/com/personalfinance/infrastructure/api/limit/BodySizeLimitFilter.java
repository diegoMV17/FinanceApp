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
import java.io.IOException;
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
            problems.write(response, ApiError.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(new LimitedBodyRequest(request, maxBytes), response);
    }

    /** La petición, con su cuerpo acotado mientras se lee. */
    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        private LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CountingStream(super.getInputStream(), maxBytes);
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

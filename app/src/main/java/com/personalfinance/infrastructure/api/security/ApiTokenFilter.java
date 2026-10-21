package com.personalfinance.infrastructure.api.security;

import com.personalfinance.infrastructure.api.error.ApiError;
import com.personalfinance.infrastructure.api.error.ApiProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * El único que decide si una petición entra.
 *
 * <p>No se llama {@code BearerTokenAuthenticationFilter} a propósito: Spring
 * Security trae una clase con ese nombre exacto, y dos nombres iguales en una
 * lista de imports es una trampa para quien venga después.
 *
 * <p>Las tres formas de fallar — sin cabecera, con una cabecera mal formada y
 * con el token equivocado — responden exactamente lo mismo. Distinguirlas le
 * diría a quien prueba si acertó la forma y solo le falta el valor, que es la
 * mitad del trabajo hecha y gratis.
 *
 * <p>El token se busca solo en la cabecera {@code Authorization}. Nunca en la
 * query: una query acaba en los logs de acceso del servidor, en el historial
 * del navegador y en el {@code Referer} de la petición siguiente.
 *
 * <p>La dirección sale siempre de {@code getRemoteAddr()}; el por qué está en
 * {@link FailedAttempts}. Aquí lo que importa es que esta clase no lee ninguna
 * cabecera que no sea {@code Authorization}.
 *
 * <p>Nada de logs con la cabecera, el token ni un trozo de ellos. Un 401 que
 * registre lo que llegó convierte el log en el sitio donde buscar el token.
 */
final class ApiTokenFilter extends OncePerRequestFilter {

    private static final String SCHEME = "Bearer";

    /**
     * Un solo usuario y una sola autoridad: o traes el token del libro o no
     * entras. No hay roles que repartir porque no hay a quién repartirlos.
     */
    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_OWNER"));

    private static final String PRINCIPAL = "owner";

    /**
     * Donde se guarda el contexto para que sobreviva al despacho a
     * {@code /error}.
     *
     * <p>Sin esto hay un fallo de los que solo se ven en producción: la
     * cadena de Spring Security también corre en el despacho de error, pero
     * este filtro no (un {@code OncePerRequestFilter} se salta ese despacho
     * por omisión). Así que al fallar algo dentro de un controlador, el
     * contexto se recargaría del repositorio, saldría vacío, y la
     * autorización convertiría ese error en un 401 de una petición que venía
     * perfectamente autenticada.
     *
     * <p>Guarda en un atributo de la petición, no en una sesión: la API no
     * tiene sesiones y no va a estrenarlas aquí.
     */
    private static final SecurityContextRepository CONTEXTS =
            new RequestAttributeSecurityContextRepository();

    private final TokenDigest digest;
    private final FailedAttempts attempts;
    private final ApiProblemWriter problems;

    ApiTokenFilter(
            TokenDigest digest, FailedAttempts attempts, ApiProblemWriter problems) {

        this.digest = digest;
        this.attempts = attempts;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String address = request.getRemoteAddr();

        // El bloqueo se mira antes del token: a quien está insistiendo no se le
        // comprueba nada, se le dice que espere.
        if (attempts.isBlocked(address)) {
            Duration remaining = attempts.blockRemaining(address);
            problems.write(response, ApiError.TOO_MANY_ATTEMPTS, remaining);
            return;
        }

        String presented = bearerTokenIn(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (presented == null || !digest.matches(presented)) {
            attempts.recordFailure(address);
            problems.write(response, ApiError.UNAUTHORIZED);
            return;
        }

        attempts.recordSuccess(address);
        authenticate(request, response);
        chain.doFilter(request, response);
    }

    private static void authenticate(
            HttpServletRequest request, HttpServletResponse response) {

        Authentication owner = UsernamePasswordAuthenticationToken.authenticated(
                PRINCIPAL, null, AUTHORITIES);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(owner);
        SecurityContextHolder.setContext(context);
        CONTEXTS.saveContext(context, request, response);
    }

    /**
     * El token de una cabecera {@code Authorization}, o {@code null} si eso no
     * es una cabecera de portador bien formada.
     *
     * <p>El esquema no distingue mayúsculas, que es lo que dice RFC 9110. El
     * resto se exige exacto: un solo espacio y algo detrás. Recortar espacios
     * de más sería aceptar como válido algo que el cliente no mandó.
     */
    private static String bearerTokenIn(String header) {
        if (header == null || header.length() <= SCHEME.length() + 1) {
            return null;
        }
        if (!header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
            return null;
        }
        if (header.charAt(SCHEME.length()) != ' ') {
            return null;
        }
        String token = header.substring(SCHEME.length() + 1);
        return token.isEmpty() || token.charAt(0) == ' ' ? null : token;
    }
}

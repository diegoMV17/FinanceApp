package com.personalfinance.infrastructure.api.security;

import com.personalfinance.infrastructure.api.error.ApiError;
import com.personalfinance.infrastructure.api.error.ApiProblemWriter;
import com.personalfinance.infrastructure.api.limit.BodySizeLimitFilter;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * La puerta: quién entra y en qué orden se le pregunta.
 *
 * <p>Se usa Spring Security en vez de un filtro propio por una razón concreta:
 * con {@code anyRequest().authenticated()} el «todo exige token» es
 * estructural. Un filtro propio sobre {@code /*} haría lo mismo hoy, pero
 * dependería de que nadie registre mañana una ruta por fuera, y esa es
 * exactamente la clase de regla que no puede depender de que alguien se
 * acuerde. Es el mismo argumento por el que el dominio no tiene Spring en el
 * classpath: la arquitectura se impone, no se recuerda.
 *
 * <p>Todo lo que Spring Security trae para una aplicación con pantallas de
 * login se apaga explícitamente. No es ruido: cada cosa apagada es una puerta
 * que no existe. Sin sesiones no hay sesión que robar; sin formulario de login
 * no hay formulario que atacar; sin anónimo no hay un «usuario» que no sea el
 * dueño. CSRF se apaga porque esta API no se autentica con nada que el
 * navegador mande solo — no hay cookie ni credencial ambiental, así que una
 * página de un tercero no puede hablar en nombre de nadie; el token hay que
 * tenerlo y ponerlo a mano.
 *
 * <p>El orden de los dos filtros propios es parte de la seguridad, no una
 * preferencia:
 *
 * <ol>
 *   <li>El tope de cuerpo va <strong>antes</strong> de la cadena de Spring
 *       Security, por orden de registro. Si fuera después, alguien sin token
 *       podría hacer que el proceso lea un cuerpo enorme para rechazarlo al
 *       final, y el trabajo ya estaría hecho.
 *   <li>El token va justo antes de {@link AuthorizationFilter}, que es después
 *       de que Spring Security haya cargado el contexto. Ponerlo antes de eso
 *       haría que el cargador de contexto sobreescribiera con uno vacío lo que
 *       este filtro acaba de autenticar.
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
class ApiSecurityConfiguration {

    /**
     * Antes que la cadena de Spring Security, que se registra en
     * {@code -100}.
     */
    private static final int BODY_LIMIT_ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Bean
    ApiProblemWriter apiProblemWriter(JsonMapper json) {
        return new ApiProblemWriter(json);
    }

    @Bean
    TokenDigest tokenDigest(ApiTokenProperties properties) {
        return new TokenDigest(properties);
    }

    @Bean
    FailedAttempts failedAttempts(ApiLimitsProperties limits, Clock clock) {
        return new FailedAttempts(limits, clock);
    }

    /**
     * El tope de cuerpo, registrado como filtro del contenedor y no de Spring
     * Security, para que corra antes que toda la cadena.
     *
     * <p>El filtro se construye aquí dentro a propósito: expuesto como
     * {@code @Bean} de tipo {@code Filter}, Boot lo registraría una segunda vez
     * por su cuenta y el cuerpo se contaría dos veces.
     */
    @Bean
    FilterRegistrationBean<BodySizeLimitFilter> bodySizeLimitRegistration(
            ApiLimitsProperties limits, ApiProblemWriter problems) {

        FilterRegistrationBean<BodySizeLimitFilter> registration =
                new FilterRegistrationBean<>(new BodySizeLimitFilter(limits, problems));

        registration.addUrlPatterns("/*");
        registration.setOrder(BODY_LIMIT_ORDER);
        return registration;
    }

    /**
     * La cadena, con el filtro del token construido aquí dentro.
     *
     * <p>Y no como {@code @Bean}, que es lo natural y es un error: un bean de
     * tipo {@code Filter} lo registra Boot <em>también</em> como filtro del
     * contenedor, por su cuenta y fuera de esta cadena. Hoy esa segunda copia
     * no hace daño porque queda detrás y {@code OncePerRequestFilter} la deja
     * pasar de largo, pero el daño que podría hacer es serio: una copia
     * corriendo fuera de la cadena pondría el contexto de seguridad sin que
     * nadie lo limpie después, y eso se filtra al siguiente que use ese hilo.
     * Que no sea un bean es la única forma de que no dependa de la aritmética
     * de los órdenes de registro.
     */
    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            TokenDigest digest,
            FailedAttempts attempts,
            ApiProblemWriter problems)
            throws Exception {

        return http
                // Deny by default: ninguna excepción, ninguna ruta suelta. No
                // hay actuator ni consola web que exceptuar porque no están en
                // el classpath, y no van a estarlo.
                .authorizeHttpRequests(routes -> routes.anyRequest().authenticated())
                .sessionManagement(sessions ->
                        sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(
                        new RequestAttributeSecurityContextRepository()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .requestCache(RequestCacheConfigurer::disable)
                // La red por si la autorización rechaza algo que el filtro del
                // token no vio, como el despacho a /error: también en ese caso
                // la respuesta es un problema de RFC 9457 y no HTML.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, failure) ->
                                problems.write(response, ApiError.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, denied) ->
                                problems.write(response, ApiError.FORBIDDEN)))
                .addFilterBefore(
                        new ApiTokenFilter(digest, attempts, problems),
                        AuthorizationFilter.class)
                .build();
    }
}

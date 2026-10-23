package com.personalfinance.infrastructure.api.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe un problema de RFC 9457 directamente en la respuesta.
 *
 * <p>Hace falta porque los filtros corren antes de Spring MVC: un 401, un 429 o
 * un 413 se deciden cuando todavía no hay controlador, así que el
 * {@code @RestControllerAdvice} no los ve nunca. Sin esto, esas tres respuestas
 * saldrían con el cuerpo que decida el contenedor — en el mejor caso vacío, en
 * el peor una página HTML de Tomcat — y la API tendría dos formatos de error
 * según quién rechazó la petición.
 *
 * <p>Se serializa con el mismo {@link JsonMapper} que usa el resto de la
 * aplicación, no con un JSON escrito a mano. Un JSON a mano funcionaría hoy,
 * porque estos cuerpos solo llevan texto fijo, pero sería una segunda forma de
 * generar JSON esperando a que alguien le meta un dato variable sin escapar.
 *
 * <p>Deliberadamente sin {@code instance}: la ruta pedida la escribió quien
 * llama, y no hay razón para devolverle lo que mandó.
 */
public final class ApiProblemWriter {

    private final JsonMapper json;

    public ApiProblemWriter(JsonMapper json) {
        this.json = json;
    }

    /** El cuerpo de un error, con su texto fijo y nada más. */
    public static ProblemDetail problemFor(ApiError error) {
        ProblemDetail problem = ProblemDetail.forStatus(error.status());
        problem.setType(java.net.URI.create(error.type()));
        problem.setTitle(error.status().getReasonPhrase());
        problem.setDetail(error.detail());
        return problem;
    }

    public void write(HttpServletResponse response, ApiError error) throws IOException {
        write(response, error, null);
    }

    /**
     * @param retryAfter cuánto esperar, para un 429 o un 503; {@code null} si no
     *                   aplica. Va en segundos y no como fecha: una fecha
     *                   obligaría al cliente a tener el reloj en hora.
     */
    public void write(HttpServletResponse response, ApiError error, Duration retryAfter)
            throws IOException {

        // Si la respuesta ya salió, escribir otra vez solo añade basura al
        // final de la que el cliente ya está leyendo.
        if (response.isCommitted()) {
            return;
        }

        response.reset();
        response.setStatus(error.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Explicito porque este cuerpo se escribe en dos sitios distintos. Los
        // 401 y 429 salen de dentro de la cadena de Spring Security, que ya
        // pone esta cabecera; el 413 lo escribe un filtro del contenedor que
        // corre por fuera, y ahi no la pone nadie. Una respuesta de error sin
        // nosniff es una que el navegador puede decidir interpretar como otra
        // cosa.
        response.setHeader("X-Content-Type-Options", "nosniff");

        if (retryAfter != null) {
            response.setHeader(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(Math.max(1, retryAfter.toSeconds())));
        }
        if (error == ApiError.UNAUTHORIZED) {
            // RFC 9110 pide decir qué esquema se espera. No lleva realm: el
            // realm no aporta nada aquí y es texto de más en cada 401.
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        json.writeValue(response.getOutputStream(), problemFor(error));
    }
}

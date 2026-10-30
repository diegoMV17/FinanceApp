package com.personalfinance.infrastructure.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.application.BookBusyException;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.UnknownAccountException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.TypeMismatchException;

/**
 * H5. El cuerpo de un error, mirado de cerca.
 *
 * <p>Sin Spring: lo que se prueba es la decisión del advice, no el transporte.
 * El caso del 500 solo se puede probar así, porque provocar un fallo inesperado
 * de verdad por HTTP exigiría romper algo a propósito dentro de la aplicación.
 */
@DisplayName("El cuerpo de un error")
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("H5 · un 500 lleva un identificador y ningún detalle")
    void a500CarriesAnIdAndNothingElse() {
        String secret = "el nombre de una tabla y la clave de la base";

        ResponseEntity<ProblemDetail> answer =
                handler.handleAnythingElse(new IllegalStateException(secret));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(answer.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        ProblemDetail problem = answer.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getDetail())
                .as("el texto fijo, nunca el mensaje de la excepción")
                .isEqualTo(ApiError.INTERNAL_ERROR.detail())
                .doesNotContain(secret);

        assertThat(problem.getProperties()).containsKey("errorId");
        assertThat(UUID.fromString(String.valueOf(problem.getProperties().get("errorId"))))
                .as("el identificador sirve para buscar en el log")
                .isNotNull();
    }

    @Test
    @DisplayName("H5 · un id o una fecha mal formados en la ruta no son «el cuerpo»")
    void aMalformedPathIsNotABody() {
        // Antes los dos casos caian en MALFORMED_REQUEST, cuyo texto dice "el
        // cuerpo de la peticion no se pudo leer". Para un GET sin cuerpo eso es
        // simplemente falso, y manda a buscar el error donde no esta: lo que
        // estaba mal era el UUID de la ruta o el asOf del query.
        ResponseEntity<Object> answer = handler.handleExceptionInternal(
                new TypeMismatchException("no-soy-un-uuid", UUID.class),
                null,
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                null);

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.getBody())
                .isInstanceOf(ProblemDetail.class)
                .extracting(body -> ((ProblemDetail) body).getDetail())
                .isEqualTo(ApiError.INVALID_PATH.detail());
    }

    @Test
    @DisplayName("H5 · y dos 500 llevan identificadores distintos")
    void two500sHaveDifferentIds() {
        Object first = handler.handleAnythingElse(new IllegalStateException("a"))
                .getBody().getProperties().get("errorId");
        Object second = handler.handleAnythingElse(new IllegalStateException("b"))
                .getBody().getProperties().get("errorId");

        // Un identificador fijo no servirá para encontrar nada en el log.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("H5 · una regla del libro no es un 500 aunque pase por ese brazo")
    void aLedgerRuleIsNotA500() {
        // handleAnythingElse atrapa Exception, asi que tambien le llegan las
        // del dominio si el brazo especifico no estuviera: tiene que
        // reclasificarlas en vez de taparlas con un 500.
        ResponseEntity<ProblemDetail> answer = handler.handleAnythingElse(
                new UnknownAccountException(new AccountId(UUID.randomUUID())));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(answer.getBody().getProperties()).isNull();
    }

    @Test
    @DisplayName("H5 · el libro ocupado lleva Retry-After en segundos")
    void abusyBookCarriesRetryAfter() {
        ResponseEntity<ProblemDetail> answer =
                handler.handleBusyBook(new BookBusyException(Duration.ofSeconds(5)));

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(answer.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
    }

    @Test
    @DisplayName("H5 · y nunca menos de un segundo")
    void neverLessThanASecond() {
        // Un Retry-After de 0 es una invitación a reintentar en bucle cerrado.
        ResponseEntity<ProblemDetail> answer =
                handler.handleBusyBook(new BookBusyException(Duration.ofMillis(200)));

        assertThat(answer.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    @Test
    @DisplayName("H5 · una regla del dominio no lleva el mensaje del dominio")
    void aDomainRuleDoesNotCarryItsMessage() {
        UnknownAccountException refusal =
                new UnknownAccountException(new AccountId(UUID.randomUUID()));

        ResponseEntity<ProblemDetail> answer = handler.handleLedgerRule(refusal);

        assertThat(answer.getBody().getDetail())
                .isEqualTo(ApiError.NOT_FOUND.detail())
                .isNotEqualTo(refusal.getMessage());
    }
}

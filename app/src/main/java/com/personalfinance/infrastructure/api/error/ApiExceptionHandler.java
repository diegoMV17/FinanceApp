package com.personalfinance.infrastructure.api.error;

import com.personalfinance.application.BookBusyException;
import com.personalfinance.domain.ledger.LedgerException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Lo único que un cliente llega a ver cuando algo falla.
 *
 * <p>La regla que esta clase existe para cumplir: <strong>ningún mensaje de
 * excepción sale de aquí</strong>. Los de {@link LedgerException} están escritos
 * para el dueño del libro y traen nombres de cuentas y montos — «Mamá»,
 * «Alimentación», 25.000 —; los de Spring traen nombres de clase, de campo y a
 * veces trozos de JSON; los de la base traen nombres de tabla. Al log va la
 * excepción entera, al cliente el texto fijo de {@link ApiError}.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} y reescribe
 * {@code handleExceptionInternal} en un solo sitio, en vez de poner un
 * {@code @ExceptionHandler} por cada excepción de Spring MVC. Es deliberado:
 * todas las de Spring pasan por ahí, así que también las que esta clase no
 * conoce y las que añada una versión futura. Un método por excepción dejaría
 * fuera, en silencio, cualquiera que no estuviera en la lista.
 *
 * <p>Nada de cuerpos de petición ni de respuesta en el log, ni de la cabecera
 * {@code Authorization}. Lo que se registra es la excepción y, para un 500, un
 * identificador que también viaja al cliente: es lo que permite que el dueño
 * diga «me salió este código» y que eso se pueda buscar, sin que el error le
 * cuente nada a nadie más.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** El nombre de la restricción propia, tal como lo reporta Bean Validation. */
    private static final String SAFE_TEXT = "SafeText";

    /**
     * Lo que el libro rechaza: 404, 409 o 422 según de quién sea el problema.
     *
     * <p>No se registra nada. Que una regla de negocio se cumpla no es un
     * incidente, y el mensaje — que es lo único interesante que trae — no puede
     * ir a ningún sitio donde se mezcle con los logs de aplicación: para eso
     * está la auditoría del dominio, que ya guarda qué pasó con cada movimiento.
     */
    @ExceptionHandler(LedgerException.class)
    ResponseEntity<ProblemDetail> handleLedgerRule(LedgerException refused) {
        return problem(ApiErrors.forFailure(refused));
    }

    /**
     * El libro está ocupado: 503 y cuánto esperar.
     *
     * <p>El {@code Retry-After} va en segundos, con un mínimo de uno: en
     * segundos un cliente no necesita tener el reloj en hora, y una fecha sí lo
     * exigiría.
     */
    @ExceptionHandler(BookBusyException.class)
    ResponseEntity<ProblemDetail> handleBusyBook(BookBusyException busy) {
        long seconds = Math.max(1, busy.waited().toSeconds());

        return ResponseEntity.status(ApiError.BOOK_BUSY.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(ApiProblemWriter.problemFor(ApiError.BOOK_BUSY));
    }

    /**
     * Cualquier otra cosa: 500 con un identificador y nada más.
     *
     * <p>Este es el brazo que impide que un fallo inesperado se convierta en una
     * descripción de las tripas de la aplicación. El cliente recibe un código
     * para preguntar; el detalle se queda en el log.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleAnythingElse(Exception unexpected) {
        ApiError known = ApiErrors.forFailure(unexpected);
        if (known != ApiError.INTERNAL_ERROR) {
            return problem(known);
        }

        String errorId = UUID.randomUUID().toString();
        log.error("Fallo inesperado atendiendo una peticion. errorId={}", errorId, unexpected);

        ProblemDetail body = ApiProblemWriter.problemFor(ApiError.INTERNAL_ERROR);
        body.setProperty("errorId", errorId);

        return ResponseEntity.status(ApiError.INTERNAL_ERROR.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /**
     * El embudo por el que pasa todo lo de Spring MVC.
     *
     * <p>Se descarta el {@code body} que venía: es el {@link ProblemDetail} que
     * arma Spring, con su propio {@code detail} y, según la excepción, la lista
     * de campos que fallaron o un trozo del JSON que no se pudo leer. Lo que
     * sale es el texto fijo del {@link ApiError} que corresponda.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception failure,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ApiError error = errorFor(failure, status);

        return ResponseEntity.status(error.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblemWriter.problemFor(error));
    }

    /** Un problema de RFC 9457 con su estado, y nada más dentro. */
    private static ResponseEntity<ProblemDetail> problem(ApiError error) {
        return ResponseEntity.status(error.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblemWriter.problemFor(error));
    }

    private static ApiError errorFor(Exception failure, HttpStatusCode status) {
        if (failure instanceof MethodArgumentNotValidException invalid) {
            return unsafeTextIn(invalid) ? ApiError.UNSAFE_TEXT : ApiError.INVALID_REQUEST;
        }
        return ApiErrors.forFrameworkFailure(failure, status);
    }

    /**
     * Si lo que falló fue la restricción de texto, para poder decirlo con su
     * propio código en vez de un «campos inválidos» genérico.
     *
     * <p>Se dice el <em>tipo</em> de problema, nunca el texto que lo causó: un
     * carácter invisible devuelto en la respuesta es el mismo carácter
     * invisible, ahora en otra pantalla.
     */
    private static boolean unsafeTextIn(MethodArgumentNotValidException invalid) {
        return invalid.getBindingResult().getAllErrors().stream()
                .anyMatch(error -> SAFE_TEXT.equals(error.getCode()));
    }
}

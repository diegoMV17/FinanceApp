package com.personalfinance.infrastructure.api.error;

import org.springframework.http.HttpStatus;

/**
 * Todo lo que la API puede responder cuando algo sale mal, y nada más.
 *
 * <p>Un sitio, una lista cerrada. Esa es la propiedad que hace comprobable la
 * regla de que ningún mensaje del dominio llega al cliente: si el {@code detail}
 * de toda respuesta de error tiene que ser uno de estos textos, un mensaje
 * filtrado no puede estar en la lista, y el test falla sin que nadie haya tenido
 * que imaginarse la fuga concreta.
 *
 * <p>Los mensajes de {@code LedgerException} traen nombres de cuentas y montos
 * — «Mamá», «Alimentación», 25.000 — porque están escritos para el dueño del
 * libro, no para un desconocido. Al log va la excepción entera; al cliente, el
 * texto fijo de aquí.
 *
 * <p>El código estable es el {@code type} de RFC 9457, que es justo para lo que
 * existe: un identificador del tipo de problema que el cliente puede comparar.
 * Un campo {@code code} aparte sería el mismo dato con dos nombres.
 */
public enum ApiError {

    /**
     * Sin token, con un token mal formado o con uno equivocado: la misma
     * respuesta para los tres. Distinguirlas le diría a quien prueba si el
     * problema es la forma o el valor, que es exactamente la pista que no
     * necesita.
     */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "unauthorized",
            "Falta un token válido."),

    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden",
            "Esta petición no está permitida."),

    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "too-many-attempts",
            "Demasiados intentos fallidos desde esta dirección."),

    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "malformed-request",
            "El cuerpo de la petición no se pudo leer."),

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "invalid-request",
            "La petición tiene campos inválidos o ausentes."),

    /**
     * Texto con caracteres invisibles que el dominio no ve: los de dirección
     * (U+202E y compañía) y los separadores U+2028 y U+2029, que
     * {@code Character.isISOControl} no cuenta como de control.
     */
    UNSAFE_TEXT(HttpStatus.BAD_REQUEST, "unsafe-text",
            "El texto trae caracteres que no se admiten."),

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type",
            "Esta API solo acepta application/json."),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
            "Ese método no se admite en esta ruta."),

    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found",
            "No existe lo que se pidió."),

    CONTENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "content-too-large",
            "El cuerpo de la petición es demasiado grande."),

    DUPLICATE_ID(HttpStatus.CONFLICT, "duplicate-id",
            "Ese identificador ya está usado con otro contenido."),

    DUPLICATE_ACCOUNT_NAME(HttpStatus.CONFLICT, "duplicate-account-name",
            "Ya hay una cuenta abierta de ese tipo con ese nombre."),

    ALREADY_CANCELLED(HttpStatus.CONFLICT, "already-cancelled",
            "Ese movimiento ya estaba anulado de otra forma."),

    ALREADY_ARCHIVED(HttpStatus.CONFLICT, "already-archived",
            "Esa cuenta ya estaba archivada."),

    ACCOUNT_STILL_HOLDS_MONEY(HttpStatus.CONFLICT, "account-still-holds-money",
            "Esa cuenta todavía tiene saldo."),

    /**
     * El brazo de recogida de {@code LedgerException}. Una regla del dominio
     * nueva cae aquí y responde 422, en vez de escaparse como un 500 que diría
     * «esto es un error del servidor» cuando es el libro diciendo que no.
     */
    RULE_VIOLATED(HttpStatus.UNPROCESSABLE_CONTENT, "rule-violated",
            "El libro no admite esta operación."),

    BOOK_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "book-busy",
            "El libro está ocupado; reintente."),

    /**
     * Lo único que el cliente recibe de un error inesperado, además de un
     * identificador con el que preguntar. El detalle vive en el log.
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
            "Error interno.");

    private static final String TYPE_PREFIX = "urn:finance:error:";

    private final HttpStatus status;
    private final String slug;
    private final String detail;

    ApiError(HttpStatus status, String slug, String detail) {
        this.status = status;
        this.slug = slug;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    /** El código estable, como URI, que es lo que RFC 9457 pide en {@code type}. */
    public String type() {
        return TYPE_PREFIX + slug;
    }

    /** El texto fijo. Nunca el mensaje de una excepción. */
    public String detail() {
        return detail;
    }
}

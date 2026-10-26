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
     * Un identificador o una fecha de la ruta o del query que no tiene la
     * forma que debería.
     *
     * <p>Separado de {@link #MALFORMED_REQUEST} porque ese texto habla del
     * <em>cuerpo</em>, y estos fallan en un {@code GET} que no lleva ninguno:
     * decirle «el cuerpo no se pudo leer» a quien escribió mal un UUID en la
     * URL lo manda a buscar el error donde no está.
     */
    INVALID_PATH(HttpStatus.BAD_REQUEST, "invalid-path",
            "Un identificador o una fecha de la dirección no tiene el formato esperado."),

    /**
     * Lo que rechaza el cortafuegos de Spring Security antes de que exista
     * nada más: un {@code TRACE}, un {@code %2e%2e} codificado, un parámetro
     * de ruta con {@code ;}, una barra doble.
     *
     * <p>Tiene su propio código porque sin él esto respondía 401 «falta un
     * token válido» incluso trayendo el token correcto, que es una pista falsa
     * de las caras: manda a revisar el secreto cuando lo que estaba mal era la
     * forma de la URL.
     */
    REJECTED_REQUEST(HttpStatus.BAD_REQUEST, "rejected-request",
            "La dirección de la petición tiene una forma que no se admite."),

    /**
     * Texto con caracteres invisibles que el dominio no ve: los de dirección
     * (U+202E y compañía) y los separadores U+2028 y U+2029, que
     * {@code Character.isISOControl} no cuenta como de control.
     */
    UNSAFE_TEXT(HttpStatus.BAD_REQUEST, "unsafe-text",
            "El texto trae caracteres que no se admiten."),

    /**
     * Una serie pidió más meses de los que se admiten.
     *
     * <p>Es un 400 y no un 413: lo que se pasó de tope no es el tamaño de la
     * petición sino el trabajo que encarga. Y tiene su propio código porque la
     * alternativa — recortar el rango y contestar — cambiaría la pregunta en
     * silencio, y un cliente no tendría forma de saber que el gráfico que
     * dibujó es de otro periodo.
     */
    RANGE_TOO_WIDE(HttpStatus.BAD_REQUEST, "range-too-wide",
            "El rango pedido abarca demasiados meses."),

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

    DUPLICATE_PERSON_NAME(HttpStatus.CONFLICT, "duplicate-person-name",
            "Ya hay una persona abierta con ese nombre."),

    ALREADY_CANCELLED(HttpStatus.CONFLICT, "already-cancelled",
            "Ese movimiento ya estaba anulado de otra forma."),

    ALREADY_ARCHIVED(HttpStatus.CONFLICT, "already-archived",
            "Esa cuenta ya estaba archivada."),

    ACCOUNT_STILL_HOLDS_MONEY(HttpStatus.CONFLICT, "account-still-holds-money",
            "Esa cuenta todavía tiene saldo."),

    PERSON_ALREADY_ARCHIVED(HttpStatus.CONFLICT, "person-already-archived",
            "Esa persona ya estaba archivada."),

    /**
     * Archivar a alguien con cuentas vivas esconde una deuda en vez de
     * saldarla. Es 409 y no 422 por lo mismo que la cuenta con saldo: choca con
     * algo que ya está, y el cliente sabe qué hacer — archivar primero lo que
     * es de esa persona.
     */
    PERSON_STILL_HAS_OPEN_ACCOUNTS(HttpStatus.CONFLICT, "person-still-has-open-accounts",
            "Esa persona todavía tiene cuentas abiertas."),

    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_CONTENT, "same-account-transfer",
            "Una transferencia necesita dos cuentas distintas."),

    ACCOUNT_NEEDS_NAME(HttpStatus.UNPROCESSABLE_CONTENT, "account-needs-name",
            "Una cuenta necesita un nombre."),

    PERSON_NEEDS_NAME(HttpStatus.UNPROCESSABLE_CONTENT, "person-needs-name",
            "Una persona necesita un nombre."),

    /**
     * Solo los tres tipos de cuenta que <em>son</em> alguien pueden ser de una
     * persona. Un banco no es una persona y una categoría menos: dejar que
     * «Alimentación» fuera de Juan metería un gasto propio dentro de la
     * posición con él, que es justo el número que la aplicación existe para
     * mantener separado.
     */
    ACCOUNT_IS_NOBODY(HttpStatus.UNPROCESSABLE_CONTENT, "account-cannot-belong-to-a-person",
            "Ese tipo de cuenta no puede ser de una persona."),

    ARCHIVED_PERSON(HttpStatus.UNPROCESSABLE_CONTENT, "archived-person",
            "Esa persona está archivada y no toma cuentas nuevas."),

    /**
     * Antes caía en {@link #RULE_VIOLATED}, porque una cuenta archivada solo se
     * tropezaba al registrar un movimiento y el 422 genérico bastaba. Con el
     * incremento 2 se tropieza también al vincular, que es una petición cuyo
     * único error posible es este o el de arriba: decir cuál de los dos ahorra
     * el viaje de adivinarlo.
     */
    ARCHIVED_ACCOUNT(HttpStatus.UNPROCESSABLE_CONTENT, "archived-account",
            "Esa cuenta está archivada."),

    DATE_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "date-out-of-range",
            "Esa fecha está fuera del rango que admite el libro."),

    /**
     * Un rango con los dos extremos al revés, en fechas o en valor.
     *
     * <p>Tiene su propio código porque la alternativa era contestar una lista
     * vacía: un rango del 31 al 1 no lo cumple nada, así que el libro podría
     * responder «ningún movimiento» y tener razón aritmética. Ese es justo el
     * error que hay que decir en voz alta, porque un informe vacío se lee como
     * «no gastaste nada en marzo» cuando lo que pasó es que las dos fechas
     * venían cambiadas de sitio.
     */
    REVERSED_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "reversed-range",
            "Un rango viene con el extremo inicial por encima del final."),

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

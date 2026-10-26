package com.personalfinance.infrastructure.api.error;

import com.personalfinance.application.BookBusyException;
import com.personalfinance.domain.ledger.AccountAlreadyArchivedException;
import com.personalfinance.domain.ledger.AccountCannotBelongToAPersonException;
import com.personalfinance.domain.ledger.AccountStillHoldsMoneyException;
import com.personalfinance.domain.ledger.ArchivedAccountException;
import com.personalfinance.domain.ledger.ArchivedPersonException;
import com.personalfinance.domain.ledger.BlankAccountNameException;
import com.personalfinance.domain.ledger.BlankPersonNameException;
import com.personalfinance.domain.ledger.DateOutOfRangeException;
import com.personalfinance.domain.ledger.DuplicateAccountException;
import com.personalfinance.domain.ledger.DuplicateAccountNameException;
import com.personalfinance.domain.ledger.DuplicatePersonException;
import com.personalfinance.domain.ledger.DuplicatePersonNameException;
import com.personalfinance.domain.ledger.DuplicateTransactionException;
import com.personalfinance.domain.ledger.LedgerException;
import com.personalfinance.domain.ledger.PersonAlreadyArchivedException;
import com.personalfinance.domain.ledger.PersonStillHasOpenAccountsException;
import com.personalfinance.domain.ledger.ReversedRangeException;
import com.personalfinance.domain.ledger.SameAccountTransferException;
import com.personalfinance.domain.ledger.TransactionAlreadyCancelledException;
import com.personalfinance.domain.ledger.UnknownAccountException;
import com.personalfinance.domain.ledger.UnknownPersonException;
import com.personalfinance.domain.ledger.UnknownTransactionException;
import com.personalfinance.infrastructure.api.limit.BodyTooLargeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * De qué se quejó el libro a qué responde la API.
 *
 * <p>Una función pura y sin Spring, para que se pueda probar exhaustivamente:
 * el test recorre todas las subclases de {@link LedgerException} que existen y
 * exige que cada una tenga una respuesta pensada. Una excepción de dominio nueva
 * rompe ese test, que es mucho mejor que aparecer un día como un 500.
 *
 * <p>La diferencia entre los códigos está en de quién es el problema:
 *
 * <ul>
 *   <li><strong>404</strong>: se pidió algo que no existe.
 *   <li><strong>409</strong>: choca con algo que ya está. Aquí caen los
 *       conflictos de idempotencia — el mismo id con otro contenido —, los
 *       nombres repetidos y lo que todavía está abierto cuando se quiere
 *       archivar.
 *   <li><strong>422</strong>: se entendió perfectamente y el libro dice que no.
 *       Un asiento desbalanceado, una cuenta archivada, un monto fuera de
 *       rango. El cliente no mandó nada mal formado; mandó algo que las reglas
 *       no permiten.
 *   <li><strong>503</strong>: el libro está ocupado. No es culpa de nadie y
 *       reintentar sirve, que es justo lo que dice el {@code Retry-After}.
 * </ul>
 *
 * <p>Esa distinción es lo que el bot va a necesitar para decidir si reintenta,
 * si corrige o si le avisa a su dueño.
 */
public final class ApiErrors {

    /** Para lo que lanzan el dominio y los casos de uso. */
    public static ApiError forFailure(Throwable failure) {
        return forType(failure.getClass());
    }

    /**
     * La misma clasificación, preguntando por el tipo.
     *
     * <p>Existe para que el test pueda recorrer las quince y pico subclases de
     * {@link LedgerException} sin construir una instancia de cada una: sus
     * constructores piden cuentas, montos y transacciones armadas, y montar
     * esos fixtures convertiría el test del mapeo en un test de fixtures.
     *
     * <p>Y es el único camino: {@link #forFailure} delega aquí, así que lo que
     * el test comprueba es exactamente lo que corre en producción. Dos
     * clasificaciones paralelas serían dos verdades, y la que se prueba nunca
     * es la que falla.
     *
     * <p><strong>El orden importa.</strong> El brazo de
     * {@code LedgerException} va al final porque todas las demás heredan de
     * él: subirlo convertiría cada 404 y cada 409 en un 422.
     */
    static ApiError forType(Class<?> failure) {
        if (UnknownAccountException.class.isAssignableFrom(failure)
                || UnknownTransactionException.class.isAssignableFrom(failure)
                || UnknownPersonException.class.isAssignableFrom(failure)) {
            return ApiError.NOT_FOUND;
        }
        if (DuplicateTransactionException.class.isAssignableFrom(failure)
                || DuplicateAccountException.class.isAssignableFrom(failure)
                || DuplicatePersonException.class.isAssignableFrom(failure)) {
            return ApiError.DUPLICATE_ID;
        }
        if (DuplicateAccountNameException.class.isAssignableFrom(failure)) {
            return ApiError.DUPLICATE_ACCOUNT_NAME;
        }
        if (DuplicatePersonNameException.class.isAssignableFrom(failure)) {
            return ApiError.DUPLICATE_PERSON_NAME;
        }
        if (TransactionAlreadyCancelledException.class.isAssignableFrom(failure)) {
            return ApiError.ALREADY_CANCELLED;
        }
        if (AccountAlreadyArchivedException.class.isAssignableFrom(failure)) {
            return ApiError.ALREADY_ARCHIVED;
        }
        if (AccountStillHoldsMoneyException.class.isAssignableFrom(failure)) {
            return ApiError.ACCOUNT_STILL_HOLDS_MONEY;
        }
        if (PersonAlreadyArchivedException.class.isAssignableFrom(failure)) {
            return ApiError.PERSON_ALREADY_ARCHIVED;
        }
        if (PersonStillHasOpenAccountsException.class.isAssignableFrom(failure)) {
            return ApiError.PERSON_STILL_HAS_OPEN_ACCOUNTS;
        }
        if (BookBusyException.class.isAssignableFrom(failure)) {
            return ApiError.BOOK_BUSY;
        }
        if (BodyTooLargeException.class.isAssignableFrom(failure)) {
            return ApiError.CONTENT_TOO_LARGE;
        }
        if (SameAccountTransferException.class.isAssignableFrom(failure)) {
            return ApiError.SAME_ACCOUNT_TRANSFER;
        }
        if (BlankAccountNameException.class.isAssignableFrom(failure)) {
            return ApiError.ACCOUNT_NEEDS_NAME;
        }
        if (BlankPersonNameException.class.isAssignableFrom(failure)) {
            return ApiError.PERSON_NEEDS_NAME;
        }
        if (AccountCannotBelongToAPersonException.class.isAssignableFrom(failure)) {
            return ApiError.ACCOUNT_IS_NOBODY;
        }
        if (ArchivedPersonException.class.isAssignableFrom(failure)) {
            return ApiError.ARCHIVED_PERSON;
        }
        if (ArchivedAccountException.class.isAssignableFrom(failure)) {
            return ApiError.ARCHIVED_ACCOUNT;
        }
        if (DateOutOfRangeException.class.isAssignableFrom(failure)) {
            return ApiError.DATE_OUT_OF_RANGE;
        }
        if (ReversedRangeException.class.isAssignableFrom(failure)) {
            return ApiError.REVERSED_RANGE;
        }
        // Siempre el último de los conocidos: es el padre de todos los de
        // arriba, y es lo que hace que una regla nueva del dominio responda
        // 422 en vez de un 500 que diría que la culpa es del servidor.
        if (LedgerException.class.isAssignableFrom(failure)) {
            return ApiError.RULE_VIOLATED;
        }
        return ApiError.INTERNAL_ERROR;
    }

    /**
     * Para lo que lanza Spring MVC antes de que el controlador llegue a correr.
     *
     * <p>Se mira la cadena de causas buscando un {@link BodyTooLargeException}:
     * el contador del cuerpo estalla dentro del flujo de entrada, así que quien
     * la ve primero es el conversor de JSON, que la envuelve en un «no pude leer
     * el cuerpo». Sin esta búsqueda, un cuerpo troceado demasiado grande
     * respondería 400 diciendo que el JSON está mal, cuando el JSON estaba bien
     * y lo que pasó es que era enorme.
     *
     * <p>El {@code status} que trae Spring es la última palabra para lo que no
     * se reconoce, de forma que un 405 o un 415 que esta clase no nombre siga
     * respondiendo su código y no un 500.
     */
    public static ApiError forFrameworkFailure(Throwable failure, HttpStatusCode status) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof BodyTooLargeException) {
                return ApiError.CONTENT_TOO_LARGE;
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return forStatus(status);
    }

    private static ApiError forStatus(HttpStatusCode status) {
        if (status.equals(HttpStatus.NOT_FOUND)) {
            return ApiError.NOT_FOUND;
        }
        if (status.equals(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ApiError.METHOD_NOT_ALLOWED;
        }
        if (status.equals(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return ApiError.UNSUPPORTED_MEDIA_TYPE;
        }
        if (status.equals(HttpStatus.CONTENT_TOO_LARGE)) {
            return ApiError.CONTENT_TOO_LARGE;
        }
        if (status.equals(HttpStatus.UNAUTHORIZED)) {
            return ApiError.UNAUTHORIZED;
        }
        if (status.equals(HttpStatus.FORBIDDEN)) {
            return ApiError.FORBIDDEN;
        }
        if (status.is4xxClientError()) {
            return ApiError.MALFORMED_REQUEST;
        }
        return ApiError.INTERNAL_ERROR;
    }

    private ApiErrors() {
    }
}

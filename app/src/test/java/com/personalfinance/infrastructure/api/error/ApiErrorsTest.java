package com.personalfinance.infrastructure.api.error;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.personalfinance.domain.ledger.SameAccountTransferException;
import com.personalfinance.domain.ledger.TransactionAlreadyCancelledException;
import com.personalfinance.domain.ledger.UnknownAccountException;
import com.personalfinance.domain.ledger.UnknownPersonException;
import com.personalfinance.domain.ledger.UnknownTransactionException;
import com.personalfinance.infrastructure.api.limit.BodyTooLargeException;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * H2. Que ninguna queja del libro se escape como un 500.
 *
 * <p>El test que de verdad importa es el primero, y no comprueba un caso: los
 * comprueba todos. Recorre las subclases de {@link LedgerException} que
 * existen de verdad en el código y exige que cada una tenga una respuesta
 * pensada. El incremento 2 trajo ocho excepciones nuevas y este test se puso
 * rojo con cada una hasta que alguien decidió qué responde la API — que es
 * exactamente cuándo hay que decidirlo.
 */
@DisplayName("La traducción de los fallos")
class ApiErrorsTest {

    private static final List<JavaClass> LEDGER_EXCEPTIONS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.personalfinance.domain")
            .stream()
            .filter(imported -> imported.isAssignableTo(LedgerException.class))
            .filter(imported -> !imported.getName().equals(LedgerException.class.getName()))
            .toList();

    @Test
    @DisplayName("H2 · hay excepciones de dominio que encontrar")
    void theDomainHasExceptions() {
        // Si el importador dejara de encontrar clases, el test de abajo pasaría
        // sin comprobar nada. Esto es el seguro de ese seguro.
        assertThat(LEDGER_EXCEPTIONS)
                .as("las subclases de LedgerException del dominio")
                .hasSizeGreaterThanOrEqualTo(23);
    }

    @Test
    @DisplayName("H2 · todas tienen una respuesta pensada, y ninguna es 500")
    void everyLedgerExceptionIsAnswered() {
        for (JavaClass refusal : LEDGER_EXCEPTIONS) {
            ApiError answer = ApiErrors.forType(classOf(refusal));

            assertThat(answer)
                    .as("%s no tiene respuesta en la API", refusal.getSimpleName())
                    .isNotEqualTo(ApiError.INTERNAL_ERROR);

            assertThat(answer.status().value())
                    .as("%s responde un código de cliente, no de servidor",
                            refusal.getSimpleName())
                    .isBetween(400, 499);
        }
    }

    @Test
    @DisplayName("H2 · y una regla nueva del dominio cae en 422, no en 500")
    void aBrandNewRuleFallsIntoUnprocessable() {
        // Esta es la excepción que todavía no existe. El brazo de recogida de
        // LedgerException es lo que hace que responda 422, y este test es el
        // que lo exige: sin él, nadie notaría que falta hasta que el incremento
        // 2 estrenara una regla y la API contestara "error del servidor".
        LedgerException invented = new LedgerException("una regla que nadie ha escrito aún") {
        };

        assertThat(ApiErrors.forFailure(invented)).isEqualTo(ApiError.RULE_VIOLATED);
        assertThat(ApiError.RULE_VIOLATED.status())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /**
     * Cada queja con nombre propio y lo que le toca responder.
     *
     * <p>Es la tabla que el brazo de recogida de {@code LedgerException} se
     * tragaría entera si subiera un sitio en {@code forType}.
     */
    private static final Map<Class<?>, ApiError> ANSWERS = Map.ofEntries(
            Map.entry(UnknownAccountException.class, ApiError.NOT_FOUND),
            Map.entry(UnknownTransactionException.class, ApiError.NOT_FOUND),
            Map.entry(DuplicateTransactionException.class, ApiError.DUPLICATE_ID),
            Map.entry(DuplicateAccountException.class, ApiError.DUPLICATE_ID),
            Map.entry(DuplicateAccountNameException.class, ApiError.DUPLICATE_ACCOUNT_NAME),
            Map.entry(TransactionAlreadyCancelledException.class, ApiError.ALREADY_CANCELLED),
            Map.entry(AccountAlreadyArchivedException.class, ApiError.ALREADY_ARCHIVED),
            Map.entry(AccountStillHoldsMoneyException.class,
                    ApiError.ACCOUNT_STILL_HOLDS_MONEY),
            Map.entry(SameAccountTransferException.class, ApiError.SAME_ACCOUNT_TRANSFER),
            Map.entry(BlankAccountNameException.class, ApiError.ACCOUNT_NEEDS_NAME),
            Map.entry(DateOutOfRangeException.class, ApiError.DATE_OUT_OF_RANGE),
            Map.entry(BookBusyException.class, ApiError.BOOK_BUSY),
            Map.entry(BodyTooLargeException.class, ApiError.CONTENT_TOO_LARGE),
            Map.entry(UnknownPersonException.class, ApiError.NOT_FOUND),
            Map.entry(DuplicatePersonException.class, ApiError.DUPLICATE_ID),
            Map.entry(DuplicatePersonNameException.class, ApiError.DUPLICATE_PERSON_NAME),
            Map.entry(PersonAlreadyArchivedException.class, ApiError.PERSON_ALREADY_ARCHIVED),
            Map.entry(PersonStillHasOpenAccountsException.class,
                    ApiError.PERSON_STILL_HAS_OPEN_ACCOUNTS),
            Map.entry(BlankPersonNameException.class, ApiError.PERSON_NEEDS_NAME),
            Map.entry(AccountCannotBelongToAPersonException.class, ApiError.ACCOUNT_IS_NOBODY),
            Map.entry(ArchivedPersonException.class, ApiError.ARCHIVED_PERSON),
            Map.entry(ArchivedAccountException.class, ApiError.ARCHIVED_ACCOUNT));

    @Test
    @DisplayName("H2 · y el brazo de recogida no se traga a las que tienen nombre")
    void theCatchAllDoesNotSwallowTheNamedOnes() {
        // El javadoc de forType dice que el orden de los brazos importa; esto
        // es lo único que lo exige. Lo encontró una prueba de mutación: subir
        // el brazo de LedgerException al principio convierte cada 404 y cada
        // 409 en un 422 y no ponía rojo a nadie, porque lo que se comprobaba
        // era que toda queja tuviera una respuesta 4xx distinta de 500, y un
        // 422 cumple las dos cosas. Un 404 que se vuelve 422 le dice al
        // cliente «el libro dice no» de una cuenta que no existe, y eso manda
        // a buscar el error donde no está.
        ANSWERS.forEach((refusal, expected) ->
                assertThat(ApiErrors.forType(refusal))
                        .as("%s", refusal.getSimpleName())
                        .isEqualTo(expected));
    }

    @Test
    @DisplayName("H2 · el libro ocupado es 503 y no un error de nadie")
    void abusyBookIsTemporary() {
        ApiError answer = ApiErrors.forFailure(new BookBusyException(Duration.ofSeconds(5)));

        assertThat(answer).isEqualTo(ApiError.BOOK_BUSY);
        assertThat(answer.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("H2 · lo que de verdad es inesperado sí es 500")
    void therealSurprisesAre500() {
        assertThat(ApiErrors.forFailure(new IllegalStateException("roto")))
                .isEqualTo(ApiError.INTERNAL_ERROR);
        assertThat(ApiErrors.forFailure(new NullPointerException()))
                .isEqualTo(ApiError.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("H2 · ningún texto de error trae datos del libro")
    void noFixedTextLeaksData() {
        for (ApiError error : ApiError.values()) {
            assertThat(error.detail())
                    .as("%s", error)
                    .isNotBlank()
                    // Un texto fijo no puede llevar huecos para rellenar: en el
                    // momento en que alguien le mete un %s, deja de ser fijo.
                    .doesNotContain("%s", "%d", "{", "}");

            assertThat(error.type())
                    .as("%s", error)
                    .startsWith("urn:finance:error:");
        }
    }

    @Test
    @DisplayName("H2 · y cada código es de un solo error")
    void everyCodeBelongsToOneError() {
        List<String> types = java.util.Arrays.stream(ApiError.values())
                .map(ApiError::type)
                .toList();

        assertThat(types).doesNotHaveDuplicates();
    }

    /** La clase de verdad, a partir de lo que encontró el importador. */
    private static Class<?> classOf(JavaClass refusal) {
        try {
            return Class.forName(refusal.getName());
        } catch (ClassNotFoundException impossible) {
            throw new AssertionError("no se encontró " + refusal.getName(), impossible);
        }
    }
}

package com.personalfinance.infrastructure.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalfinance.application.BookBusyException;
import com.personalfinance.domain.ledger.LedgerException;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * H2. Que ninguna queja del libro se escape como un 500.
 *
 * <p>El test que de verdad importa es el primero, y no comprueba un caso: los
 * comprueba todos. Recorre las subclases de {@link LedgerException} que
 * existen de verdad en el código y exige que cada una tenga una respuesta
 * pensada. Cuando el incremento 2 traiga reglas nuevas con excepciones nuevas,
 * este test se pondrá rojo hasta que alguien decida qué responde la API — que
 * es exactamente cuándo hay que decidirlo.
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
                .hasSizeGreaterThanOrEqualTo(15);
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

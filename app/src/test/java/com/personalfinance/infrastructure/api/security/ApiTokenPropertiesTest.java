package com.personalfinance.infrastructure.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * S1 y S2. El token de la API, antes de que exista una petición.
 *
 * <p>Esto no prueba HTTP: prueba que un token inservible impide el arranque en
 * vez de descubrirse el día que alguien apunte al servidor. Un token corto es
 * tan malo como no tener token, y uno con un salto de línea al final — lo que
 * deja un archivo de secreto leído sin cuidado — es un token distinto del que
 * su dueño cree que puso.
 */
@DisplayName("El token de la API")
class ApiTokenPropertiesTest {

    /** 43 caracteres: los 32 bytes aleatorios de la decisión, en base64url. */
    private static final String VALID = "mQ8kR2vN7pLxT4wZ9bY6cH1jF5dS3gA0eU8iO2nK7rM";

    @Nested
    @DisplayName("al construirse")
    class WhenBuilt {

        @Test
        @DisplayName("S1 · acepta uno de 32 bytes o más")
        void acceptsALongEnoughToken() {
            assertThatNoException().isThrownBy(() -> new ApiTokenProperties(VALID));
            assertThat(new ApiTokenProperties(VALID).token()).isEqualTo(VALID);
        }

        @Test
        @DisplayName("S1 · rechaza que falte")
        void rejectsAMissingToken() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties(null));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties(""));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties("   "));
        }

        @Test
        @DisplayName("S1 · rechaza uno más corto que 32 bytes")
        void rejectsAShortToken() {
            String thirtyOne = "a".repeat(ApiTokenProperties.MIN_TOKEN_BYTES - 1);

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties(thirtyOne));

            assertThatNoException()
                    .isThrownBy(() -> new ApiTokenProperties(
                            "a".repeat(ApiTokenProperties.MIN_TOKEN_BYTES)));
        }

        @Test
        @DisplayName("S1 · cuenta bytes y no caracteres")
        void countsBytesAndNotCharacters() {
            // Dieciséis eñes son dieciséis caracteres y treinta y dos bytes en
            // UTF-8. Medir con length() las rechazaría, y son un token
            // perfectamente largo: la decisión dice bytes.
            assertThat("ñ".repeat(16).length()).isEqualTo(16);
            assertThatNoException().isThrownBy(() -> new ApiTokenProperties("ñ".repeat(16)));
        }

        @Test
        @DisplayName("S1 · rechaza espacios y caracteres de control")
        void rejectsWhitespaceAndControlCharacters() {
            // El caso real: un secreto leído de un archivo se trae el salto de
            // línea. Recortarlo en silencio dejaría guardado un token distinto
            // del que se configuró; fallar al arrancar lo dice en voz alta.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties(VALID + "\n"));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties(VALID + " "));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties(VALID.replace('k', '\t')));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties(VALID.replace('k', '\u0000')));
        }
    }

    @Nested
    @DisplayName("al ligarse a la configuración")
    class WhenBound {

        @Test
        @DisplayName("S1 · no tiene valor por omisión: sin API_TOKEN no arranca")
        void thereIsNoDefault() {
            // Esta es la mitad que un test del constructor no cubre: que nadie
            // haya puesto un @DefaultValue. Si lo hubiera, un servidor al que
            // le faltara la variable arrancaría con una clave que está
            // publicada en este repositorio, y la API quedaría abierta para
            // cualquiera que haya leido el código.
            Binder empty = new Binder(new MapConfigurationPropertySource());

            assertThatExceptionOfType(Exception.class)
                    .isThrownBy(() ->
                            empty.bindOrCreate("finance.api", ApiTokenProperties.class));
        }

        @Test
        @DisplayName("S1 · y un token corto en el yml tampoco deja arrancar")
        void ashortTokenInTheYamlAlsoFails() {
            MapConfigurationPropertySource written = new MapConfigurationPropertySource();
            written.put("finance.api.token", "corto");

            assertThatExceptionOfType(Exception.class)
                    .isThrownBy(() -> new Binder(written)
                            .bindOrCreate("finance.api", ApiTokenProperties.class));
        }

        @Test
        @DisplayName("S1 · uno bueno sí se liga")
        void agoodOneBinds() {
            MapConfigurationPropertySource written = new MapConfigurationPropertySource();
            written.put("finance.api.token", VALID);

            ApiTokenProperties bound = new Binder(written)
                    .bindOrCreate("finance.api", ApiTokenProperties.class);

            assertThat(bound.token()).isEqualTo(VALID);
        }
    }

    @Nested
    @DisplayName("una vez construido")
    class OnceBuilt {

        @Test
        @DisplayName("S2 · no se imprime nunca")
        void neverPrintsItself() {
            // Un record imprime todos sus componentes. Este objeto acaba en
            // mensajes de arranque, en volcados del contexto y en cualquier log
            // que concatene un bean, así que su toString no puede traer el
            // secreto.
            String printed = new ApiTokenProperties(VALID).toString();

            assertThat(printed).doesNotContain(VALID);
            assertThat(printed).doesNotContain(VALID.substring(0, 8));
        }

        @Test
        @DisplayName("S2 · tampoco lo imprime el mensaje que lo rechaza")
        void neverPrintsTheRejectedToken() {
            String rejected = "corto";

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new ApiTokenProperties(rejected))
                    .withMessageNotContaining(rejected);
        }
    }
}

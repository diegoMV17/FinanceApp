package com.personalfinance.infrastructure.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S3. La única comparación del token.
 *
 * <p>Vive en una clase propia y diminuta por una razón: que el token se compare
 * en un solo sitio es lo que hace revisable la promesa de tiempo constante. Si
 * la comparación estuviera repartida, nadie podría afirmar nada sobre ella.
 *
 * <p>Lo que estos tests <em>no</em> prueban es que la comparación sea de tiempo
 * constante: eso no se observa desde un test funcional, y decir lo contrario
 * sería cobertura inventada. Lo que prueban es que acepta el token correcto y
 * rechaza todo lo demás, incluidos los casos que una comparación perezosa
 * dejaría pasar: un prefijo, un sufijo, y algo larguísimo.
 */
@DisplayName("La comparación del token")
class TokenDigestTest {

    private static final String SECRET = "mQ8kR2vN7pLxT4wZ9bY6cH1jF5dS3gA0eU8iO2nK7rM";

    private final TokenDigest digest = new TokenDigest(new ApiTokenProperties(SECRET));

    @Test
    @DisplayName("S3 · acepta el token configurado")
    void acceptsTheConfiguredToken() {
        assertThat(digest.matches(SECRET)).isTrue();
    }

    @Test
    @DisplayName("S3 · rechaza uno distinto del mismo largo")
    void rejectsADifferentTokenOfTheSameLength() {
        String sameLength = SECRET.substring(0, SECRET.length() - 1) + "X";

        assertThat(sameLength).hasSameSizeAs(SECRET).isNotEqualTo(SECRET);
        assertThat(digest.matches(sameLength)).isFalse();
    }

    @Test
    @DisplayName("S3 · rechaza un prefijo y un sufijo del correcto")
    void rejectsPrefixesAndSuffixes() {
        assertThat(digest.matches(SECRET.substring(0, SECRET.length() - 1))).isFalse();
        assertThat(digest.matches(SECRET + "X")).isFalse();
        assertThat(digest.matches("X" + SECRET)).isFalse();
    }

    @Test
    @DisplayName("S3 · rechaza lo vacío y lo ausente")
    void rejectsNothing() {
        assertThat(digest.matches(null)).isFalse();
        assertThat(digest.matches("")).isFalse();
    }

    @Test
    @DisplayName("S3 · rechaza algo larguísimo sin romperse")
    void rejectsSomethingHuge() {
        // Diez mil caracteres no pueden hacer saltar una excepción: una
        // excepción aquí sería un 500 donde el cliente merece un 401, y un 500
        // es información que un 401 no da.
        assertThat(digest.matches("a".repeat(10_000))).isFalse();
    }

    @Test
    @DisplayName("S3 · distingue tokens que solo difieren en acentos")
    void doesNotNormalizeUnicode() {
        // "ñ" compuesta y descompuesta se ven igual en una pantalla y son dos
        // tokens distintos. Comparar bytes de UTF-8 es lo correcto: un token no
        // es texto para leer, es un secreto para cotejar.
        TokenDigest accented = new TokenDigest(new ApiTokenProperties("ñ".repeat(16)));

        assertThat(accented.matches("ñ".repeat(16))).isTrue();
        assertThat(accented.matches("ñ".repeat(16))).isFalse();
    }
}

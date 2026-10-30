package com.personalfinance.infrastructure.api.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El único token que abre la API.
 *
 * <p>Viene de la variable {@code API_TOKEN} y no tiene valor por omisión, igual
 * que las credenciales de la base: si falta, la aplicación no arranca. Un
 * {@code @DefaultValue} aquí sería una clave publicada en este repositorio, y
 * una API cuyo token todo el mundo conoce es una API sin token.
 *
 * <p>Las comprobaciones viven en el constructor, no en un chequeo al recibir la
 * primera petición, porque un token inservible tiene que impedir el arranque:
 * descubrirlo cuando alguien ya está apuntando al servidor es tarde. Lo que se
 * puede comprobar es el tamaño y la forma; que los 32 bytes sean de verdad
 * aleatorios no lo puede verificar nadie desde aquí, y por eso está escrito en
 * la decisión y no en un {@code if}.
 *
 * <p>Se mide en bytes y no en caracteres: la decisión habla de 32 bytes de
 * azar, y dieciséis eñes son dieciséis caracteres pero treinta y dos bytes.
 *
 * <p>Un marcador sin resolver también se rechaza, y esto <strong>no</strong> es
 * paranoia: Spring no falla cuando {@code ${API_TOKEN}} no existe en el
 * entorno, le pasa a este constructor la cadena literal {@code "${API_TOKEN}"}.
 * Son doce bytes, así que hoy lo para el mínimo de treinta y dos — por
 * accidente. El día que alguien renombre la variable a algo más largo que eso,
 * la aplicación arrancaría con el nombre del marcador como token: un secreto de
 * doce caracteres que está escrito en este repositorio y que adivina cualquiera
 * que haya leído el {@code application.yml}. Mejor que la protección sea
 * deliberada.
 *
 * <p>Los espacios y los caracteres de control se rechazan en vez de recortarse.
 * Es el caso real de un secreto leído de un archivo, que se trae el salto de
 * línea: recortarlo en silencio dejaría funcionando un token distinto del que
 * su dueño cree que configuró, y el día que rote el secreto no entendería por
 * qué el viejo sigue sirviendo.
 */
@ConfigurationProperties("finance.api")
public record ApiTokenProperties(String token) {

    /**
     * Treinta y dos bytes. Por debajo de eso, un token deja de ser algo que hay
     * que robar y pasa a ser algo que se puede adivinar.
     */
    public static final int MIN_TOKEN_BYTES = 32;

    public ApiTokenProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "finance.api.token es obligatorio: defina la variable API_TOKEN");
        }
        if (token.codePoints().anyMatch(
                codePoint -> Character.isWhitespace(codePoint)
                        || Character.isISOControl(codePoint))) {

            throw new IllegalArgumentException(
                    "finance.api.token no puede traer espacios ni caracteres de control; "
                            + "revise si el secreto llegó con un salto de línea al final");
        }
        if (token.startsWith("${") && token.endsWith("}")) {
            throw new IllegalArgumentException(
                    "finance.api.token quedó sin resolver: defina la variable de entorno "
                            + "que nombra application.yml");
        }
        if (token.getBytes(StandardCharsets.UTF_8).length < MIN_TOKEN_BYTES) {
            throw new IllegalArgumentException(
                    "finance.api.token tiene que traer al menos " + MIN_TOKEN_BYTES + " bytes");
        }
    }

    /**
     * Sin el token, a propósito.
     *
     * <p>Un record imprime todos sus componentes, y este objeto es un bean: lo
     * imprime un volcado del contexto, un mensaje de arranque o cualquier log
     * que concatene un bean sin pensarlo. El valor por omisión de un record es
     * aquí una fuga esperando a que alguien escriba un {@code log.debug}.
     */
    @Override
    public String toString() {
        return "ApiTokenProperties[token=(oculto)]";
    }
}

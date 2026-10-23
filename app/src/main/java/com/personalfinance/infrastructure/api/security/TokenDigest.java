package com.personalfinance.infrastructure.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * El único sitio donde se compara el token.
 *
 * <p>Se compara el SHA-256 de lo que llegó contra el SHA-256 de lo configurado,
 * no las cadenas. Dos resúmenes miden siempre treinta y dos bytes, y eso es lo
 * que hace la comparación de tiempo constante de verdad: {@link
 * MessageDigest#isEqual} recorre todos los bytes cuando los dos arreglos miden
 * igual, pero sale de inmediato cuando no, así que comparar los tokens en bruto
 * delataría su longitud en el tiempo de respuesta. Con los resúmenes, la
 * longitud del secreto no entra en la ecuación.
 *
 * <p>El resumen de lo configurado se calcula una vez, al construir, y no en
 * cada petición: menos trabajo por petición y, sobre todo, el secreto se lee
 * una sola vez en toda la vida del proceso.
 *
 * <p>Esta clase es diminuta a propósito. Que la comparación viva en un solo
 * sitio es lo que permite afirmar algo sobre ella en una revisión; repartida
 * por un filtro, nadie podría.
 */
final class TokenDigest {

    private static final String ALGORITHM = "SHA-256";

    private final byte[] expected;

    TokenDigest(ApiTokenProperties properties) {
        this.expected = digestOf(properties.token());
    }

    /**
     * @param presented lo que trajo la petición, que puede no traer nada
     * @return si es exactamente el token configurado
     */
    boolean matches(String presented) {
        // Que no venga nada no es un secreto que proteger: no hay con qué
        // comparar y el tiempo que tarde este caso no dice nada del token.
        if (presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected, digestOf(presented));
    }

    private static byte[] digestOf(String value) {
        try {
            return MessageDigest.getInstance(ALGORITHM)
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 es obligatorio en toda implementación de Java.
            throw new IllegalStateException("falta " + ALGORITHM, impossible);
        }
    }
}

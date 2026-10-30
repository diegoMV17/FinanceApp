package com.personalfinance.infrastructure.api.limit;

/**
 * El cuerpo pasó del tope mientras se leía.
 *
 * <p>Existe como excepción propia, y no como un {@code IOException}, porque es
 * lo que permite distinguir «el cliente mandó demasiado» de «la conexión se
 * cortó». Lo primero es un 413 y es culpa de quien llama; lo segundo no es ni
 * una cosa ni la otra.
 *
 * <p>Se lanza desde dentro del flujo de entrada, así que quien la ve primero es
 * el conversor de JSON, que la envuelve. El {@code @RestControllerAdvice}
 * recorre las causas para encontrarla: sin eso, un cuerpo troceado demasiado
 * grande respondería 400 («no se pudo leer el JSON») en vez del 413 que explica
 * qué pasó.
 */
public final class BodyTooLargeException extends RuntimeException {

    private final long limitInBytes;

    BodyTooLargeException(long limitInBytes) {
        super("el cuerpo de la petición pasó de " + limitInBytes + " bytes");
        this.limitInBytes = limitInBytes;
    }

    public long limitInBytes() {
        return limitInBytes;
    }
}

package com.personalfinance.infrastructure.persistence;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cuánto espera una escritura su turno antes de rendirse.
 *
 * <p>Cinco segundos: lo suficiente para que una escritura normal —
 * milisegundos — nunca lo alcance, y lo bastante poco para que un cliente que
 * ya se cansó reciba una respuesta en vez de quedarse colgado. Pasado ese
 * tiempo no se escribió nada y reintentar es seguro.
 *
 * <p>El valor por defecto vive aquí y no en {@code application.yml}: una sola
 * fuente de verdad, y la aplicación arranca igual sin configuración. Se valida
 * al construirse, así que un valor absurdo en el archivo o en una variable de
 * entorno impide el arranque en vez de aparecer como un error raro la primera
 * vez que dos peticiones coincidan.
 */
@ConfigurationProperties("finance.book-lock")
public record BookLockProperties(@DefaultValue("5s") Duration timeout) {

    /**
     * Un minuto es más de lo que cualquier escritura de este libro justifica;
     * más allá de eso el problema es otro y esperar solo lo esconde.
     */
    private static final Duration LONGEST_SENSIBLE_WAIT = Duration.ofMinutes(1);

    public BookLockProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException(
                    "finance.book-lock.timeout tiene que ser positivo");
        }
        if (timeout.compareTo(LONGEST_SENSIBLE_WAIT) > 0) {
            throw new IllegalArgumentException(
                    "finance.book-lock.timeout no puede pasar de " + LONGEST_SENSIBLE_WAIT);
        }
    }
}

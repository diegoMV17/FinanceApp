package com.personalfinance.infrastructure.api.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Los topes del borde HTTP.
 *
 * <p>Los valores por omisión viven aquí y no en {@code application.yml}, igual
 * que en {@code BookLockProperties}: una sola fuente de verdad, y la aplicación
 * arranca igual sin que nadie escriba estas claves.
 *
 * <p>Las comprobaciones están en el constructor porque un tope absurdo tiene
 * que impedir el arranque. Un {@code max-body-size} de {@code 64} — queriendo
 * decir 64 KiB y escribiendo 64 bytes — dejaría una API que rechaza todas las
 * peticiones, y es mejor enterarse al arrancar que por un 413 inexplicable.
 */
@ConfigurationProperties("finance.api.limits")
public record ApiLimitsProperties(
        @DefaultValue("64KB") DataSize maxBodySize,
        @DefaultValue("10") int failedAttempts,
        @DefaultValue("1m") Duration blockFor,
        @DefaultValue("1024") int trackedAddresses) {

    /**
     * El cuerpo más grande que este libro puede necesitar es una corrección con
     * su movimiento anidado, y no llega al kilobyte. Un mega es ya el tope de lo
     * que tiene sentido aceptar; por encima, lo que se está pidiendo es otra
     * cosa.
     */
    private static final DataSize LARGEST_SENSIBLE_BODY = DataSize.ofMegabytes(1);

    /** Mil intentos ya no son un dueño equivocándose, son alguien probando. */
    private static final int MOST_SENSIBLE_ATTEMPTS = 1_000;

    /** Más de una hora de bloqueo deja al dueño fuera de su propia app. */
    private static final Duration LONGEST_SENSIBLE_BLOCK = Duration.ofHours(1);

    public ApiLimitsProperties {
        if (maxBodySize == null
                || maxBodySize.toBytes() <= 0
                || maxBodySize.compareTo(LARGEST_SENSIBLE_BODY) > 0) {

            throw new IllegalArgumentException(
                    "finance.api.limits.max-body-size tiene que estar entre 1 byte y "
                            + LARGEST_SENSIBLE_BODY);
        }
        if (failedAttempts < 1 || failedAttempts > MOST_SENSIBLE_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "finance.api.limits.failed-attempts tiene que estar entre 1 y "
                            + MOST_SENSIBLE_ATTEMPTS);
        }
        if (blockFor == null
                || blockFor.isNegative()
                || blockFor.isZero()
                || blockFor.compareTo(LONGEST_SENSIBLE_BLOCK) > 0) {

            throw new IllegalArgumentException(
                    "finance.api.limits.block-for tiene que ser positivo y no pasar de "
                            + LONGEST_SENSIBLE_BLOCK);
        }
        if (trackedAddresses < 1) {
            throw new IllegalArgumentException(
                    "finance.api.limits.tracked-addresses tiene que ser al menos 1");
        }
    }
}

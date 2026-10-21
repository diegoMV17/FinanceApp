package com.personalfinance.infrastructure.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;

/**
 * S8. Los topes del borde, antes de la primera petición.
 *
 * <p>El caso que justifica estas comprobaciones es mecanografiar {@code 64}
 * queriendo decir 64 KiB: la aplicación arrancaría tan contenta y rechazaría
 * todas las peticiones con un 413 que nadie sabría explicar. Vale más no
 * arrancar.
 */
@DisplayName("Los topes del borde")
class ApiLimitsPropertiesTest {

    private static final DataSize OK_BODY = DataSize.ofKilobytes(64);
    private static final Duration OK_BLOCK = Duration.ofMinutes(1);

    @Test
    @DisplayName("S8 · los valores por omisión viven en el código, no en el yml")
    void defaultsLiveInCode() {
        // Igual que BookLockProperties: la aplicación arranca aunque nadie
        // escriba una sola clave de finance.api.limits.
        ApiLimitsProperties bound = new Binder(new MapConfigurationPropertySource())
                .bindOrCreate("finance.api.limits", ApiLimitsProperties.class);

        assertThat(bound.maxBodySize()).isEqualTo(DataSize.ofKilobytes(64));
        assertThat(bound.failedAttempts()).isEqualTo(10);
        assertThat(bound.blockFor()).isEqualTo(Duration.ofMinutes(1));
        assertThat(bound.trackedAddresses()).isEqualTo(1024);
    }

    @Test
    @DisplayName("S8 · rechaza un cuerpo de cero o negativo")
    void rejectsAnEmptyBodyCap() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(
                        DataSize.ofBytes(0), 10, OK_BLOCK, 1024));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(
                        DataSize.ofBytes(-1), 10, OK_BLOCK, 1024));
    }

    @Test
    @DisplayName("S8 · rechaza un cuerpo absurdamente grande")
    void rejectsARidiculousBodyCap() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(
                        DataSize.ofMegabytes(2), 10, OK_BLOCK, 1024));

        assertThatNoException().isThrownBy(() -> new ApiLimitsProperties(
                DataSize.ofMegabytes(1), 10, OK_BLOCK, 1024));
    }

    @Test
    @DisplayName("S8 · rechaza un tope de intentos que no limita nada")
    void rejectsAnUselessAttemptLimit() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(OK_BODY, 0, OK_BLOCK, 1024));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(OK_BODY, -3, OK_BLOCK, 1024));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(OK_BODY, 10_000, OK_BLOCK, 1024));
    }

    @Test
    @DisplayName("S8 · rechaza un bloqueo nulo o eterno")
    void rejectsASillyBlock() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(OK_BODY, 10, Duration.ZERO, 1024));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(
                        OK_BODY, 10, Duration.ofSeconds(-1), 1024));
        // Más de una hora deja al dueño fuera de su propia aplicación.
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(
                        OK_BODY, 10, Duration.ofHours(2), 1024));
    }

    @Test
    @DisplayName("S8 · rechaza un mapa de direcciones de tamaño cero")
    void rejectsTrackingNobody() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ApiLimitsProperties(OK_BODY, 10, OK_BLOCK, 0));
    }

    @Test
    @DisplayName("S8 · un 64 suelto son 64 bytes, y por eso hay un tope mínimo")
    void aBareNumberMeansBytes() {
        // Esto es el error que se quiere atrapar: escribir 64 pensando en KiB.
        // Se acepta porque 64 bytes es un tope válido aunque inútil, así que lo
        // que protege de verdad es el test de arriba sobre el yml y este
        // recordatorio de que la unidad hay que escribirla.
        MapConfigurationPropertySource written = new MapConfigurationPropertySource();
        written.put("finance.api.limits.max-body-size", "64");

        ApiLimitsProperties bound = new Binder(written)
                .bindOrCreate("finance.api.limits", ApiLimitsProperties.class);

        assertThat(bound.maxBodySize()).isEqualTo(DataSize.ofBytes(64));
        assertThat(bound.maxBodySize()).isNotEqualTo(DataSize.ofKilobytes(64));
    }
}

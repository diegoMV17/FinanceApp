package com.personalfinance.infrastructure.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * S4. El límite de intentos fallidos por dirección.
 *
 * <p>Un token de 32 bytes no se adivina a fuerza bruta, así que esto no es la
 * defensa principal: la defensa es el token. Lo que compra es que un intento
 * tras otro deje de ser gratis, y que el log diga algo cuando alguien lo está
 * intentando.
 *
 * <p>El reloj se inyecta porque la alternativa es un test que duerme un minuto
 * para comprobar que el bloqueo expira, y un test que duerme es un test que
 * algún día se salta.
 */
@DisplayName("El límite de intentos")
class FailedAttemptsTest {

    private static final String ONE = "10.0.0.1";
    private static final String OTHER = "10.0.0.2";
    private static final int MAX = 3;
    private static final Duration BLOCK_FOR = Duration.ofMinutes(1);
    private static final int TRACKED = 4;

    private TickingClock clock;
    private FailedAttempts attempts;

    @BeforeEach
    void startFresh() {
        clock = new TickingClock(Instant.parse("2026-10-20T12:00:00Z"));
        attempts = new FailedAttempts(
                new ApiLimitsProperties(DataSize.ofKilobytes(64), MAX, BLOCK_FOR, TRACKED),
                clock);
    }

    @Nested
    @DisplayName("al contar")
    class WhenCounting {

        @Test
        @DisplayName("S4 · no bloquea a quien no ha fallado")
        void doesNotBlockTheInnocent() {
            assertThat(attempts.isBlocked(ONE)).isFalse();
        }

        @Test
        @DisplayName("S4 · bloquea justo al llegar al tope, no antes")
        void blocksExactlyAtTheLimit() {
            // El borde importa en las dos direcciones: con MAX - 1 fallos
            // todavía se responde 401, y es el fallo número MAX el que cambia
            // la respuesta a 429.
            for (int i = 0; i < MAX - 1; i++) {
                attempts.recordFailure(ONE);
                assertThat(attempts.isBlocked(ONE)).isFalse();
            }

            attempts.recordFailure(ONE);

            assertThat(attempts.isBlocked(ONE)).isTrue();
        }

        @Test
        @DisplayName("S4 · un acierto borra la cuenta")
        void aSuccessClearsTheCount() {
            attempts.recordFailure(ONE);
            attempts.recordFailure(ONE);

            attempts.recordSuccess(ONE);

            // Si el acierto no borrara, el dueño que se equivoca dos veces y
            // luego entra bien quedaría a un fallo del bloqueo para siempre.
            attempts.recordFailure(ONE);
            assertThat(attempts.isBlocked(ONE)).isFalse();
        }

        @Test
        @DisplayName("S4 · cada dirección cuenta por su cuenta")
        void addressesAreIndependent() {
            for (int i = 0; i < MAX; i++) {
                attempts.recordFailure(ONE);
            }

            assertThat(attempts.isBlocked(ONE)).isTrue();
            assertThat(attempts.isBlocked(OTHER)).isFalse();
        }
    }

    @Nested
    @DisplayName("al expirar")
    class WhenExpiring {

        @Test
        @DisplayName("S4 · el bloqueo se levanta solo")
        void theBlockLiftsByItself() {
            for (int i = 0; i < MAX; i++) {
                attempts.recordFailure(ONE);
            }
            assertThat(attempts.isBlocked(ONE)).isTrue();

            clock.advance(BLOCK_FOR.plusSeconds(1));

            assertThat(attempts.isBlocked(ONE)).isFalse();
        }

        @Test
        @DisplayName("S4 · y el conteo empieza de cero")
        void andTheCountStartsOver() {
            for (int i = 0; i < MAX; i++) {
                attempts.recordFailure(ONE);
            }
            clock.advance(BLOCK_FOR.plusSeconds(1));

            attempts.recordFailure(ONE);

            assertThat(attempts.isBlocked(ONE)).isFalse();
        }

        @Test
        @DisplayName("S4 · dice cuánto falta, para el Retry-After")
        void saysHowLongIsLeft() {
            for (int i = 0; i < MAX; i++) {
                attempts.recordFailure(ONE);
            }

            clock.advance(Duration.ofSeconds(20));

            assertThat(attempts.blockRemaining(ONE)).isEqualTo(Duration.ofSeconds(40));
            assertThat(attempts.blockRemaining(OTHER)).isZero();
        }
    }

    @Nested
    @DisplayName("al crecer")
    class WhenGrowing {

        @Test
        @DisplayName("S4 · el mapa no crece sin límite")
        void theMapIsBounded() {
            // Sin tope, cualquiera con direcciones que rotar convierte el
            // limitador en la forma de tumbar el proceso por memoria. El
            // limitador no puede ser el agujero.
            for (int i = 0; i < 5_000; i++) {
                attempts.recordFailure("10.1." + (i / 256) + "." + (i % 256));
            }

            assertThat(attempts.trackedAddresses()).isLessThanOrEqualTo(TRACKED);
        }

        @Test
        @DisplayName("S4 · y un bloqueo vivo no se desaloja")
        void aLiveBlockIsNotEvicted() {
            for (int i = 0; i < MAX; i++) {
                attempts.recordFailure(ONE);
            }
            assertThat(attempts.isBlocked(ONE)).isTrue();

            // Llenar el mapa con direcciones nuevas no puede ser la manera de
            // quitarse el propio bloqueo de encima.
            for (int i = 0; i < 5_000; i++) {
                attempts.recordFailure("10.2." + (i / 256) + "." + (i % 256));
            }

            assertThat(attempts.isBlocked(ONE)).isTrue();
        }

        @Test
        @DisplayName("S4 · lo caducado deja sitio a lo nuevo")
        void staleEntriesMakeRoom() {
            for (int i = 0; i < TRACKED; i++) {
                attempts.recordFailure("10.3.0." + i);
            }
            assertThat(attempts.trackedAddresses()).isEqualTo(TRACKED);

            clock.advance(BLOCK_FOR.plusSeconds(1));
            attempts.recordFailure(ONE);

            assertThat(attempts.isBlocked(ONE)).isFalse();
            for (int i = 0; i < MAX - 1; i++) {
                attempts.recordFailure(ONE);
            }
            assertThat(attempts.isBlocked(ONE)).isTrue();
        }
    }

    /** Un reloj que avanza cuando el test lo dice y no cuando pasa el tiempo. */
    private static final class TickingClock extends Clock {

        private Instant now;

        private TickingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

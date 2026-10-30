package com.personalfinance.infrastructure.api.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuántas veces ha fallado cada dirección, y hasta cuándo se le dice que no.
 *
 * <p>No es la defensa contra la fuerza bruta: la defensa son los 32 bytes del
 * token, que no se adivinan. Lo que esto compra es que intentarlo deje de ser
 * gratis y que haya algo que mirar cuando alguien lo esté intentando.
 *
 * <p>La dirección es siempre {@code getRemoteAddr()}. Nunca
 * {@code X-Forwarded-For}: delante no hay ningún proxy de confianza, así que esa
 * cabecera la escribe quien llama. Creerle regalaría dos cosas a la vez —
 * saltarse el limitador rotando un valor inventado, y llenar este mapa con
 * claves que nadie puede acotar.
 *
 * <p>El mapa está acotado porque si no, el limitador sería el agujero: con
 * direcciones que rotar, cualquiera podría tumbar el proceso por memoria. Al
 * llenarse se barre primero lo caducado; si aún no cabe, se descarta antes una
 * dirección sin bloqueo que una bloqueada. Es deliberado en esa dirección y no
 * en la otra: quien pueda rotar direcciones se salta el limitador de todas
 * formas, pero desalojar un bloqueo vivo convertiría el llenar el mapa en la
 * manera de quitárselo de encima.
 *
 * <p>Sin estado compartido entre procesos, a propósito: un solo usuario, un
 * solo proceso, y un contador en memoria que se pierde al reiniciar es
 * exactamente lo que hace falta aquí.
 */
final class FailedAttempts {

    private final int max;
    private final Duration blockFor;
    private final int trackedAddresses;
    private final Clock clock;

    private final Map<String, Attempts> byAddress = new ConcurrentHashMap<>();

    FailedAttempts(ApiLimitsProperties limits, Clock clock) {
        this.max = limits.failedAttempts();
        this.blockFor = limits.blockFor();
        this.trackedAddresses = limits.trackedAddresses();
        this.clock = clock;
    }

    /** Si a esta dirección hay que responderle 429 sin mirarle el token. */
    boolean isBlocked(String address) {
        Attempts seen = byAddress.get(address);
        return seen != null && seen.blocks(clock.instant(), max, blockFor);
    }

    /** Cuánto falta para levantar el bloqueo, para el {@code Retry-After}. */
    Duration blockRemaining(String address) {
        Instant now = clock.instant();
        Attempts seen = byAddress.get(address);
        if (seen == null || !seen.blocks(now, max, blockFor)) {
            return Duration.ZERO;
        }
        return Duration.between(now, seen.last().plus(blockFor));
    }

    void recordFailure(String address) {
        Instant now = clock.instant();

        byAddress.compute(address, (ignored, seen) -> {
            if (seen == null || seen.hasExpired(now, blockFor)) {
                return new Attempts(1, now);
            }
            return new Attempts(seen.failures() + 1, now);
        });

        makeRoomIfNeeded(now);
    }

    /**
     * Entrar bien borra la cuenta.
     *
     * <p>Sin esto, el dueño que se equivoca unas cuantas veces a lo largo de una
     * semana quedaría a un fallo del bloqueo para siempre: lo que importa son
     * los fallos seguidos, no los de toda la vida.
     */
    void recordSuccess(String address) {
        byAddress.remove(address);
    }

    int trackedAddresses() {
        return byAddress.size();
    }

    private void makeRoomIfNeeded(Instant now) {
        if (byAddress.size() <= trackedAddresses) {
            return;
        }
        byAddress.entrySet().removeIf(entry -> entry.getValue().hasExpired(now, blockFor));

        // Si barrer lo caducado no alcanzó, se sacrifica una dirección que solo
        // está contando antes que una que está bloqueada.
        while (byAddress.size() > trackedAddresses && dropOneUnblocked(now)) {
            // dropOneUnblocked ya quitó una; seguir hasta caber o no poder más.
        }
    }

    private boolean dropOneUnblocked(Instant now) {
        for (Iterator<Map.Entry<String, Attempts>> it = byAddress.entrySet().iterator();
                it.hasNext(); ) {

            if (!it.next().getValue().blocks(now, max, blockFor)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Cuántos fallos seguidos lleva una dirección y cuándo fue el último. */
    private record Attempts(int failures, Instant last) {

        boolean hasExpired(Instant now, Duration blockFor) {
            return !now.isBefore(last.plus(blockFor));
        }

        boolean blocks(Instant now, int max, Duration blockFor) {
            return failures >= max && !hasExpired(now, blockFor);
        }
    }
}

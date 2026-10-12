package com.personalfinance.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El reloj de la aplicación, en UTC.
 *
 * <p>El dominio sigue sin reloj: todas sus consultas reciben la fecha como
 * parámetro, porque un método que lee "ahora" por su cuenta no se puede probar
 * para el mes pasado. La hora la pone el caso de uso, y por eso el reloj es un
 * bean: en un test se reemplaza por uno fijo sin tocar nada más.
 *
 * <p>UTC y no la zona de la máquina. Las columnas son {@code TIMESTAMPTZ} y
 * Hibernate ya está configurado en UTC; que el servidor de Oracle Cloud esté en
 * otra zona no debe cambiar un solo dato guardado.
 *
 * <p>Y en microsegundos, que es la precisión de un {@code TIMESTAMPTZ}. El
 * reloj del sistema da nanosegundos, así que sin truncar aquí la fecha que
 * queda en el libro en memoria no es la misma que la base puede guardar: el
 * mismo hecho tendría dos fechas, una con cola de nanosegundos y otra sin ella,
 * y comparar la que se acabó de escribir con la que se vuelve a cargar daría
 * falso. Truncar donde nace la fecha lo arregla en un solo lugar.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    private static final Duration POSTGRES_PRECISION = Duration.ofNanos(1_000);

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), POSTGRES_PRECISION);
    }
}

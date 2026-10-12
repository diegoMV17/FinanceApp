package com.personalfinance.infrastructure.config;

import java.time.Clock;
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
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

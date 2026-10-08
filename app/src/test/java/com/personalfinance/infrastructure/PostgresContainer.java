package com.personalfinance.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * El Postgres de los tests, como bean de Spring.
 *
 * <p>Spring lo arranca antes que cualquier otro bean y lo apaga después de
 * todos, y {@code @ServiceConnection} le pasa la URL, el usuario y la clave a la
 * aplicación sin configurar nada a mano. Todas las clases que comparten contexto
 * comparten también este único contenedor; cada test limpia las tablas al
 * empezar, así que no se pisan entre sí.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}

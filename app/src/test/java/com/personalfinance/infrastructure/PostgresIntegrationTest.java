package com.personalfinance.infrastructure;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de los tests que necesitan una base de datos de verdad.
 *
 * <p>Postgres y no H2: el esquema usa triggers en plpgsql, tipos ENUM y un
 * índice único parcial. Probar contra una base que no los soporta daría verde
 * sin haber probado nada de lo que de verdad protege el libro.
 *
 * <p>El contenedor es {@code static}: se levanta una vez para toda la clase y
 * Spring Boot le pasa la conexión a la aplicación por {@code @ServiceConnection},
 * sin que haya que configurar una URL en ningún lado.
 *
 * <p>{@code disabledWithoutDocker} hace que estos tests se salten en vez de
 * fallar donde no haya Docker, para que {@code gradle test} siga sirviendo.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
abstract class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
}

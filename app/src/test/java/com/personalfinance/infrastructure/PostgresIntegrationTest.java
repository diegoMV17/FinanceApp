package com.personalfinance.infrastructure;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de los tests que necesitan una base de datos de verdad.
 *
 * <p>Postgres y no H2: el esquema usa triggers en plpgsql, tipos ENUM y un
 * índice único parcial. Probar contra una base que no los soporta daría verde
 * sin haber probado nada de lo que de verdad protege el libro.
 *
 * <p>El contenedor lo maneja Spring ({@link PostgresContainer}), no la extensión
 * de JUnit. Con un campo {@code @Container static}, JUnit apaga el contenedor al
 * terminar cada clase de test, pero Spring guarda el contexto en caché y lo
 * reutiliza en la clase siguiente: esa segunda clase queda conectada a un
 * Postgres que ya no existe y cada test espera 30 segundos antes de fallar.
 * Como bean, el contenedor vive exactamente lo mismo que el contexto que lo usa.
 *
 * <p>{@code disabledWithoutDocker} hace que estos tests se salten en vez de
 * fallar donde no haya Docker, para que {@code gradle test} siga sirviendo. Ojo:
 * eso también significa que sin Docker abierto el build sale verde sin haber
 * probado nada de esta capa.
 */
@SpringBootTest
@Import(PostgresContainer.class)
@Testcontainers(disabledWithoutDocker = true)
abstract class PostgresIntegrationTest {
}

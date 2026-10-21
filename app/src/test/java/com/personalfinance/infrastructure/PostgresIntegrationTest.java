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
 *
 * <p>El token de la API se fija aquí porque {@code application.yml} lo pide sin
 * valor por omisión y sin él no arranca ningún contexto, ni el de los tests que
 * no tocan HTTP. Es un valor fijo y público a propósito: así los tests de la API
 * saben con qué autenticarse y CI no necesita un secreto para correr. Que esto
 * esté escrito en el repositorio es exactamente la razón por la que el de
 * producción viene de {@code API_TOKEN} y nunca de un archivo versionado.
 */
@SpringBootTest(properties = "finance.api.token=" + PostgresIntegrationTest.TEST_API_TOKEN)
@Import(PostgresContainer.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    /** El token de los tests. 43 caracteres, los 32 bytes que exige la regla. */
    public static final String TEST_API_TOKEN = "tEsT0nly3xQ7wZ2pL9vN4kR8mY6bH1jF5dS3gA0eU8i";
}

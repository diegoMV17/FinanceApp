package com.personalfinance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * S15–S19. Los roles de mínimo privilegio, con el mismo script que usa el
 * despliegue.
 *
 * <p>El script se copia a {@code docker-entrypoint-initdb.d} igual que lo hace
 * el compose, con las contraseñas por el entorno: lo que se prueba es ese
 * archivo, no una copia del SQL escrita para el test, que sería una segunda
 * verdad que nadie se acordaría de mantener igual.
 *
 * <p>No extiende {@link PostgresIntegrationTest} a propósito. Ahí la conexión
 * la pone {@code @ServiceConnection} con el superusuario del contenedor, y aquí
 * lo que importa es justo lo contrario: que la aplicación trabaje como
 * {@code finance_app}, que Flyway migre como {@code finance_owner}, y que el
 * primero no pueda lo que el segundo sí. El contenedor es de esta clase y nada
 * más, y {@code @DirtiesContext} cierra el contexto con él: otra clase no
 * puede heredar un contexto conectado a un Postgres que ya se apagó.
 *
 * <p>Los rechazos se comprueban por el código de Postgres
 * ({@code 42501}, privilegio insuficiente) y no por «lanzó algo»: un
 * {@code DELETE} sobre {@code transactions} también falla por el trigger que
 * prohíbe borrar, y ese fallo diría que el trigger funciona, no que el rol
 * carece del permiso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@DisplayName("Los roles de la base")
class LeastPrivilegeRolesTest {

    /** El mismo archivo que monta {@code deploy/compose.yaml}. */
    private static final Path ROLES_SCRIPT = Path.of("../../deploy/postgres/10-roles.sh");

    private static final String OWNER = "finance_owner";
    private static final String APP = "finance_app";

    // Al azar en cada corrida: no protegen nada, pero así ningún test puede
    // depender de una contraseña escrita en el repositorio.
    private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
    private static final String APP_PASSWORD = UUID.randomUUID().toString();

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withEnv("FINANCE_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("FINANCE_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(ROLES_SCRIPT, 0755),
                    "/docker-entrypoint-initdb.d/10-roles.sh");

    @DynamicPropertySource
    static void connectAsTheRoles(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", () -> OWNER);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        registry.add("finance.api.token", () -> PostgresIntegrationTest.TEST_API_TOKEN);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("S15 · la app arranca con Flyway como dueño y trabaja como finance_app")
    void theAppMigratesAsOwnerAndWorksAsApp() throws Exception {
        assertThat(Files.isRegularFile(ROLES_SCRIPT)).as("el script del despliegue").isTrue();

        mvc.perform(post("/api/accounts")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + PostgresIntegrationTest.TEST_API_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "type": "ASSET", "name": "Nequi"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));

        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(APP);
        // La fila de la bitácora sale de un BIGSERIAL: sin USAGE en la
        // secuencia, la escritura de arriba habría sido un 500.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class)).isEqualTo(1);

        try (Connection admin = asSuperuser()) {
            assertThat(strings(admin, """
                    SELECT tablename FROM pg_tables
                    WHERE schemaname = 'public' AND tableowner <> 'finance_owner'
                    """)).as("tablas que no son del dueño").isEmpty();
            assertThat(strings(admin, """
                    SELECT DISTINCT installed_by FROM flyway_schema_history
                    """)).containsExactly(OWNER);
            assertThat(strings(admin, """
                    SELECT rolname FROM pg_roles
                    WHERE rolname IN ('finance_owner', 'finance_app')
                      AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication
                           OR rolbypassrls)
                    """)).as("roles con poderes de administración").isEmpty();
        }
    }

    @Test
    @DisplayName("S16 · finance_app no puede borrar ni truncar ninguna tabla")
    void theAppCannotDeleteOrTruncate() throws Exception {
        try (Connection admin = asSuperuser(); Connection app = asApp()) {
            List<String> tables = strings(admin,
                    "SELECT quote_ident(tablename) FROM pg_tables WHERE schemaname = 'public'");
            assertThat(tables).as("hay tablas que mirar").isNotEmpty();

            for (String table : tables) {
                refused(app, "DELETE FROM " + table);
                refused(app, "TRUNCATE " + table);
            }
        }
    }

    @Test
    @DisplayName("S17 · ni crear nada: ni tablas, ni temporales, ni esquemas, ni funciones")
    void theAppCannotCreateAnything() throws Exception {
        try (Connection app = asApp()) {
            refused(app, "CREATE TABLE intruder (id int)");
            refused(app, "CREATE TEMP TABLE intruder (id int)");
            refused(app, "CREATE SCHEMA intruder");
            refused(app, "CREATE VIEW intruder AS SELECT 1");
            refused(app, "CREATE FUNCTION intruder() RETURNS int LANGUAGE sql AS 'SELECT 1'");
        }
    }

    @Test
    @DisplayName("S18 · una tabla de una migración futura hereda los mismos permisos, y nada más")
    void aFutureTableInheritsTheSamePrivileges() throws Exception {
        try (Connection owner = asOwner(); Connection app = asApp()) {
            execute(owner, "CREATE TABLE future_table (id bigserial PRIMARY KEY, note text)");
            try {
                execute(app, "INSERT INTO future_table (note) VALUES ('a')");
                execute(app, "UPDATE future_table SET note = 'b'");
                assertThat(strings(app, "SELECT note FROM future_table")).containsExactly("b");

                refused(app, "DELETE FROM future_table");
                refused(app, "TRUNCATE future_table");
            } finally {
                execute(owner, "DROP TABLE future_table");
            }
        }
    }

    @Test
    @DisplayName("S19 · y el historial de Flyway no es asunto de la app")
    void theMigrationHistoryIsOffLimits() throws Exception {
        // ALTER DEFAULT PRIVILEGES también alcanza a esta tabla, porque la crea
        // el dueño. Con INSERT y UPDATE, un proceso comprometido podría dar por
        // aplicada una migración que nunca corrió o romper los checksums y
        // tumbar el arranque siguiente. El callback afterMigrate se lo quita.
        try (Connection app = asApp()) {
            refused(app, "SELECT * FROM flyway_schema_history");
            refused(app, "UPDATE flyway_schema_history SET checksum = 0");
            refused(app, """
                    INSERT INTO flyway_schema_history
                        (installed_rank, version, description, type, script, installed_by,
                         execution_time, success)
                    VALUES (999, '999', 'falsa', 'SQL', 'V999__falsa.sql', 'finance_app', 0, true)
                    """);
        }
    }

    private static void refused(Connection connection, String sql) {
        assertThatThrownBy(() -> execute(connection, sql))
                .as(sql)
                .isInstanceOfSatisfying(SQLException.class, refusal ->
                        assertThat(refusal.getSQLState()).as(sql).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<String> strings(Connection connection, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private static Connection asApp() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP, APP_PASSWORD);
    }

    private static Connection asOwner() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }

    private static Connection asSuperuser() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}

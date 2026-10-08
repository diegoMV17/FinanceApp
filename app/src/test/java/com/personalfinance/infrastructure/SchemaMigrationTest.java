package com.personalfinance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El esquema es la red de seguridad del dominio. Estos tests comprueban que de
 * verdad atrapa lo que dice atrapar.
 *
 * <p>Las reglas ya están probadas en Java, en milisegundos. Aquí se prueba lo
 * otro: que un script, una migración futura o un {@code psql} abierto a las dos
 * de la mañana no puedan dejar el libro en un estado imposible.
 *
 * <p>Dos decisiones que hacen que estos tests prueben algo:
 *
 * <p>{@code @Transactional(propagation = NOT_SUPPORTED)} evita que JUnit envuelva
 * cada test en una transacción con rollback. El trigger de balance es
 * {@code DEFERRABLE INITIALLY DEFERRED}: corre al cerrar la transacción de base
 * de datos. Dentro de una transacción que nunca hace commit no se dispararía
 * nunca, y el test pasaría sin haber ejercitado nada.
 *
 * <p>Y los apuntes se insertan con {@link TransactionTemplate}, en un solo
 * commit. Uno por uno en autocommit, el trigger vería la transacción con un
 * único apunte y la rechazaría siempre: estaríamos probando el autocommit, no
 * el esquema.
 *
 * <p>Las comprobaciones miran el mensaje de Postgres en la traza completa en vez
 * de un tipo de excepción concreto: un fallo que llega durante el commit viaja
 * envuelto en capas que dependen del gestor de transacciones, y fijar el tipo
 * haría frágil un test que lo que quiere verificar es la regla.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("El esquema")
class SchemaMigrationTest extends PostgresIntegrationTest {

    private static final String CASH = "11111111-1111-1111-1111-111111111111";
    private static final String FOOD = "22222222-2222-2222-2222-222222222222";
    private static final String MOMS_FUND = "33333333-3333-3333-3333-333333333333";

    private static final String SOME_DAY = "2026-10-06";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate inOneCommit;

    /** Un apunte por escribir: esta cuenta, estos centavos. */
    private record Line(String accountId, long cents) {
    }

    @BeforeEach
    void startFromAnEmptyBook() {
        inOneCommit = new TransactionTemplate(transactionManager);
        requireMigratedSchema();

        // DELETE está prohibido por los triggers, y eso es justamente el punto.
        // TRUNCATE no dispara triggers de fila, así que deja la base limpia sin
        // desactivar las protecciones que se están probando.
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts CASCADE");

        openAccount(CASH, "ASSET", "Efectivo");
        openAccount(FOOD, "EXPENSE", "Alimentación");
        openAccount(MOMS_FUND, "THIRD_PARTY_FUND", "Mamá");
    }

    @Nested
    @DisplayName("queda creado por Flyway")
    class Migration {

        @Test
        @DisplayName("P1 · que deja constancia en su propia tabla de historia")
        void flywayRecordsWhatItApplied() {
            List<String> applied = jdbc.queryForList(
                    "SELECT description FROM flyway_schema_history WHERE success",
                    String.class);

            assertThat(applied).contains("esquema inicial");
        }

        @Test
        @DisplayName("P2 · con las cuatro tablas, la vista y cada uno de sus triggers")
        void everythingIsThere() {
            assertThat(tableNames())
                    .contains("accounts", "transactions", "entries", "audit_log");
            assertThat(viewNames()).contains("account_eventual_balances");
            // Por nombre y no por cantidad: un número no dice cuál falta, y uno
            // reemplazado por otro con distinto alcance daría el mismo total.
            assertThat(triggerNames()).containsExactlyInAnyOrder(
                    "entries_must_balance",
                    "entries_are_immutable",
                    "transactions_are_never_deleted",
                    "transactions_only_cancel_once",
                    "accounts_keep_their_identity",
                    "accounts_are_never_deleted",
                    "audit_log_only_grows");
        }

        @Test
        @DisplayName("P3 · y Hibernate lo valida al arrancar")
        void hibernateValidatesAgainstIt() {
            // Que el contexto haya arrancado ya lo prueba: con ddl-auto: validate
            // Hibernate falla el arranque si el esquema no coincide con el mapeo.
            assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("exige que una transacción balancee")
    class Balance {

        @Test
        @DisplayName("P4 · y acepta la que suma cero")
        void acceptsABalancedTransaction() {
            assertThatNoException().isThrownBy(() -> record(
                    "Almuerzo",
                    new Line(CASH, -3_500_000),
                    new Line(FOOD, 3_500_000)));

            assertThat(entryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("P5 · y rechaza la que no, aunque sea por cincuenta pesos")
        void refusesAnUnbalancedTransaction() {
            assertThatThrownBy(() -> record(
                    "Mala",
                    new Line(CASH, -3_500_000),
                    new Line(FOOD, 3_505_000)))
                    .hasStackTraceContaining("no balancea");

            assertThat(entryCount()).isZero();
        }

        @Test
        @DisplayName("P6 · y rechaza una transacción con un solo apunte")
        void refusesALonelyEntry() {
            assertThatThrownBy(() -> record("Solitaria", new Line(CASH, -1_000)))
                    .hasStackTraceContaining("apunte(s)");

            assertThat(entryCount()).isZero();
        }

        @Test
        @DisplayName("P7 · y rechaza un apunte que no mueve nada")
        void refusesAZeroEntry() {
            assertThatThrownBy(() -> record(
                    "Nada",
                    new Line(CASH, 0),
                    new Line(FOOD, 0)))
                    .hasStackTraceContaining("entries_amount_not_zero");
        }

        @Test
        @DisplayName("P8 · y acepta una transacción repartida en tres apuntes")
        void acceptsASplitTransaction() {
            assertThatNoException().isThrownBy(() -> record(
                    "Almuerzo a medias",
                    new Line(CASH, -2_000_000),
                    new Line(MOMS_FUND, -1_500_000),
                    new Line(FOOD, 3_500_000)));

            assertThat(entryCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("no deja reescribir la historia")
    class Immutability {

        @Test
        @DisplayName("P9 · un apunte no se modifica")
        void entriesCannotBeUpdated() {
            lunch();

            assertThatThrownBy(() -> jdbc.update("UPDATE entries SET amount_cents = 1"))
                    .hasStackTraceContaining("inmutables");
        }

        @Test
        @DisplayName("P10 · un apunte no se borra")
        void entriesCannotBeDeleted() {
            lunch();

            assertThatThrownBy(() -> jdbc.update("DELETE FROM entries"))
                    .hasStackTraceContaining("inmutables");
        }

        @Test
        @DisplayName("P11 · una transacción no se borra, se anula")
        void transactionsCannotBeDeleted() {
            lunch();

            assertThatThrownBy(() -> jdbc.update("DELETE FROM transactions"))
                    .hasStackTraceContaining("no se borran");
        }

        @Test
        @DisplayName("P12 · y la fecha de una anulación no se reescribe")
        void cancellationsAreFinal() {
            String id = lunch();
            cancel(id, "2026-10-06T20:00:00Z");

            assertThatThrownBy(() -> cancel(id, "2026-10-06T21:00:00Z"))
                    .hasStackTraceContaining("ya fue anulada");
        }

        @Test
        @DisplayName("P13 · y una versión no admite dos correcciones")
        void aVersionIsReplacedOnlyOnce() {
            String original = lunch();
            replace(original);

            assertThatThrownBy(() -> replace(original))
                    .hasStackTraceContaining("transactions_one_replacement_each");
        }
    }

    @Nested
    @DisplayName("protege los nombres de las cuentas")
    class AccountNames {

        @Test
        @DisplayName("P14 · dos cuentas abiertas del mismo tipo no se llaman igual")
        void noDuplicateNamesAmongOpenAccounts() {
            assertThatThrownBy(() -> openAccount(newId(), "ASSET", "  efectivo  "))
                    .hasStackTraceContaining("accounts_open_name_unique");
        }

        @Test
        @DisplayName("P15 · el mismo nombre en otro tipo sí se permite")
        void theSameNameUnderAnotherType() {
            assertThatNoException()
                    .isThrownBy(() -> openAccount(newId(), "EXPENSE", "Efectivo"));
        }

        @Test
        @DisplayName("P16 · y una archivada libera su nombre")
        void archivingFreesTheName() {
            jdbc.update("UPDATE accounts SET archived_at = now() WHERE id = ?::uuid", CASH);

            assertThatNoException()
                    .isThrownBy(() -> openAccount(newId(), "ASSET", "Efectivo"));
        }

        @Test
        @DisplayName("P17 · un nombre en blanco no pasa")
        void blankNamesAreRefused() {
            assertThatThrownBy(() -> openAccount(newId(), "ASSET", "   "))
                    .hasStackTraceContaining("accounts_name_not_blank");
        }

        @Test
        @DisplayName("P18 · y una categoría no puede pertenecer a una persona")
        void onlyPersonTypesCarryAPerson() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO accounts (id, type, name, person_id) "
                            + "VALUES (?::uuid, 'EXPENSE', 'Mal', ?::uuid)",
                    newId(), newId()))
                    .hasStackTraceContaining("accounts_person_only_on_person_types");
        }
    }

    @Nested
    @DisplayName("calcula saldos")
    class Balances {

        @Test
        @DisplayName("P19 · con el signo interno de la convención")
        void keepsTheLedgerSign() {
            record("Mamá me deja plata",
                    new Line(CASH, 10_000_000),
                    new Line(MOMS_FUND, -10_000_000));

            assertThat(eventualBalanceOf(MOMS_FUND)).isEqualTo(-10_000_000L);
            assertThat(eventualBalanceOf(CASH)).isEqualTo(10_000_000L);
        }

        /**
         * Regresión. La primera versión de la vista filtraba las anuladas en el
         * {@code ON} de un {@code LEFT JOIN}: ahí el join simplemente falla, el
         * apunte sobrevive en el {@code SUM} y el saldo sale como si no se
         * hubiera anulado nada. Lo encontró una corrida contra una base real.
         */
        @Test
        @DisplayName("P20 · y deja de contar lo que se anuló")
        void ignoresCancelledTransactions() {
            String id = record("Mamá me deja plata",
                    new Line(CASH, 10_000_000),
                    new Line(MOMS_FUND, -10_000_000));

            cancel(id, "2026-10-06T20:00:00Z");

            assertThat(eventualBalanceOf(MOMS_FUND)).isZero();
            assertThat(eventualBalanceOf(CASH)).isZero();
        }

        @Test
        @DisplayName("P21 · y una cuenta sin movimientos da cero, no nada")
        void anAccountWithoutMovementsReadsZero() {
            assertThat(eventualBalanceOf(FOOD)).isZero();
        }

        @Test
        @DisplayName("P22 · y cuenta los movimientos programados a futuro")
        void countsScheduledMovements() {
            recordOn(newId(), "2026-12-24", "Regalo programado",
                    new Line(CASH, -5_000_000),
                    new Line(FOOD, 5_000_000));

            // Es la pregunta que decide si una cuenta se puede archivar:
            // no "cuánto hay hoy" sino "ya terminé con esta cuenta".
            assertThat(eventualBalanceOf(CASH)).isEqualTo(-5_000_000L);
        }

        @Test
        @DisplayName("P23 · y todo el libro, sumado, da cero")
        void theWholeBookBalances() {
            lunch();
            record("Mamá", new Line(CASH, 10_000_000), new Line(MOMS_FUND, -10_000_000));

            Long total = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount_cents), 0) FROM entries", Long.class);

            assertThat(total).isZero();
        }
    }

    @Nested
    @DisplayName("guarda quién anuló")
    class CancellationSource {

        @Test
        @DisplayName("P24 · una anulación sin origen no pasa")
        void aCancellationNeedsItsSource() {
            String id = lunch();

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE transactions SET cancelled_at = now() WHERE id = ?::uuid", id))
                    .hasStackTraceContaining("transactions_cancellation_is_complete");
        }

        @Test
        @DisplayName("P25 · ni un origen sin anulación")
        void aSourceNeedsACancellation() {
            String id = lunch();

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE transactions SET cancellation_source = 'BOT' WHERE id = ?::uuid", id))
                    .hasStackTraceContaining("transactions_cancellation_is_complete");
        }

        @Test
        @DisplayName("P26 · y una anulación completa sí queda guardada")
        void aCompleteCancellationIsStored() {
            String id = lunch();
            cancel(id, "2026-10-06T20:00:00Z");

            String stored = jdbc.queryForObject(
                    "SELECT cancellation_source::text FROM transactions WHERE id = ?::uuid",
                    String.class, id);

            assertThat(stored).isEqualTo("APP");
        }
    }

    @Nested
    @DisplayName("no deja reescribir un movimiento guardado")
    class TransactionHeader {

        @Test
        @DisplayName("P27 · ni moverlo de fecha, que movería los saldos de ese mes")
        void theDateIsFinal() {
            String id = lunch();

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE transactions SET occurred_on = '2026-01-01' WHERE id = ?::uuid", id))
                    .hasStackTraceContaining("no se modifica");
        }

        @Test
        @DisplayName("P28 · ni cambiar su descripción")
        void theDescriptionIsFinal() {
            String id = lunch();

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE transactions SET description = 'Otra cosa' WHERE id = ?::uuid", id))
                    .hasStackTraceContaining("no se modifica");
        }

        @Test
        @DisplayName("P29 · ni a cuál otro reemplaza")
        void whatItReplacesIsFinal() {
            String id = lunch();
            String other = lunch();

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE transactions SET replaces_id = ?::uuid WHERE id = ?::uuid", other, id))
                    .hasStackTraceContaining("no se modifica");
        }

        @Test
        @DisplayName("P30 · ni el origen de una anulación ya hecha")
        void theCancellationSourceIsFinal() {
            String id = lunch();
            cancel(id, "2026-10-06T20:00:00Z");

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE transactions SET cancellation_source = 'BOT' WHERE id = ?::uuid", id))
                    .hasStackTraceContaining("ya fue anulada");
        }

        @Test
        @DisplayName("P31 · ni su motivo")
        void theCancellationNoteIsFinal() {
            String id = lunch();
            cancel(id, "2026-10-06T20:00:00Z");

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE transactions SET cancellation_note = 'Otro motivo' WHERE id = ?::uuid",
                    id))
                    .hasStackTraceContaining("ya fue anulada");
        }

        @Test
        @DisplayName("P32 · ni deshacerla")
        void aCancellationCannotBeUndone() {
            String id = lunch();
            cancel(id, "2026-10-06T20:00:00Z");

            assertThatThrownBy(() -> jdbc.update("UPDATE transactions "
                    + "SET cancelled_at = NULL, cancellation_source = NULL WHERE id = ?::uuid", id))
                    .hasStackTraceContaining("ya fue anulada");
        }

        @Test
        @DisplayName("P33 · ni colgarle una segunda copia de sus apuntes")
        void itsEntriesCannotBeWrittenTwice() {
            String id = lunch();

            assertThatThrownBy(() -> inOneCommit.executeWithoutResult(status -> {
                insertEntry(id, new Line(CASH, -3_500_000), (short) 0);
                insertEntry(id, new Line(FOOD, 3_500_000), (short) 1);
            })).hasStackTraceContaining("entries_one_per_position");

            assertThat(entryCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("protege la identidad de las cuentas")
    class AccountIdentity {

        @Test
        @DisplayName("P34 · una cuenta no cambia de tipo")
        void theTypeIsFinal() {
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE accounts SET type = 'EXPENSE' WHERE id = ?::uuid", CASH))
                    .hasStackTraceContaining("no cambia de tipo");
        }

        @Test
        @DisplayName("P35 · ni se desarchiva")
        void archivingIsFinal() {
            jdbc.update("UPDATE accounts SET archived_at = now() WHERE id = ?::uuid", CASH);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE accounts SET archived_at = NULL WHERE id = ?::uuid", CASH))
                    .hasStackTraceContaining("ya fue archivada");
        }

        @Test
        @DisplayName("P36 · ni se borra")
        void accountsAreNeverDeleted() {
            assertThatThrownBy(() -> jdbc.update("DELETE FROM accounts WHERE id = ?::uuid", FOOD))
                    .hasStackTraceContaining("no se borran");
        }

        @Test
        @DisplayName("P37 · pero sí se puede renombrar")
        void renamingIsAllowed() {
            assertThatNoException().isThrownBy(() -> jdbc.update(
                    "UPDATE accounts SET name = 'Billetera' WHERE id = ?::uuid", CASH));
        }
    }

    @Nested
    @DisplayName("conserva la auditoría")
    class AuditLog {

        @Test
        @DisplayName("P38 · sin dejar editarla")
        void itCannotBeEdited() {
            audit();

            assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'CANCEL'"))
                    .hasStackTraceContaining("solo admite agregar");
        }

        @Test
        @DisplayName("P39 · ni borrarla")
        void itCannotBeDeleted() {
            audit();

            assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                    .hasStackTraceContaining("solo admite agregar");
        }

        private void audit() {
            jdbc.update("INSERT INTO audit_log (entity, entity_id, action, source) "
                    + "VALUES ('transaction', ?::uuid, 'CREATE', 'APP')", newId());
        }
    }

    // --- Ayudas. SQL crudo a propósito: lo que se prueba es el esquema, así que
    // --- cualquier capa intermedia estorbaría.

    private String record(String description, Line... lines) {
        String id = newId();
        recordOn(id, SOME_DAY, description, lines);
        return id;
    }

    /** La transacción y sus apuntes en un solo commit, como lo hará la aplicación. */
    private void recordOn(String id, String occurredOn, String description, Line... lines) {
        inOneCommit.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO transactions (id, occurred_on, description) "
                    + "VALUES (?::uuid, ?::date, ?)", id, occurredOn, description);

            for (short position = 0; position < lines.length; position++) {
                insertEntry(id, lines[position], position);
            }
        });
    }

    private String lunch() {
        return record("Almuerzo", new Line(CASH, -3_500_000), new Line(FOOD, 3_500_000));
    }

    /** Desde la V3 la posición es obligatoria y única dentro de la transacción. */
    private void insertEntry(String transactionId, Line line, short position) {
        jdbc.update("INSERT INTO entries "
                        + "(id, transaction_id, account_id, amount_cents, position) "
                        + "VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?)",
                newId(), transactionId, line.accountId(), line.cents(), position);
    }

    private void openAccount(String id, String type, String name) {
        jdbc.update("INSERT INTO accounts (id, type, name) "
                + "VALUES (?::uuid, ?::account_type, ?)", id, type, name);
    }

    private void cancel(String id, String at) {
        jdbc.update("UPDATE transactions SET cancelled_at = ?::timestamptz, "
                + "cancellation_source = 'APP' WHERE id = ?::uuid", at, id);
    }

    private void replace(String originalId) {
        String id = newId();
        inOneCommit.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO transactions "
                    + "(id, occurred_on, description, replaces_id) "
                    + "VALUES (?::uuid, ?::date, 'Corrección', ?::uuid)",
                    id, SOME_DAY, originalId);
            insertEntry(id, new Line(CASH, -4_000_000), (short) 0);
            insertEntry(id, new Line(FOOD, 4_000_000), (short) 1);
        });
    }

    private Long eventualBalanceOf(String accountId) {
        return jdbc.queryForObject(
                "SELECT ledger_cents FROM account_eventual_balances WHERE account_id = ?::uuid",
                Long.class, accountId);
    }

    private Integer entryCount() {
        return jdbc.queryForObject("SELECT count(*) FROM entries", Integer.class);
    }

    private List<String> triggerNames() {
        return jdbc.queryForList(
                "SELECT DISTINCT trigger_name FROM information_schema.triggers "
                        + "WHERE trigger_schema = 'public'", String.class);
    }

    private List<String> tableNames() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
    }

    private List<String> viewNames() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.views "
                        + "WHERE table_schema = 'public'", String.class);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Si el esquema no está, falla aquí con el motivo en vez de dejar que
     * reviente el TRUNCATE y todos los tests den el mismo stack trace sin
     * decir nada.
     *
     * <p>El caso real que motivó esto: en Spring Boot 4 las autoconfiguraciones
     * se repartieron en módulos por tecnología. Con solo {@code flyway-core} en
     * el classpath, Flyway está en el proyecto pero nadie lo arranca; la
     * aplicación levanta con la base vacía y sin una línea de log que lo delate.
     */
    private void requireMigratedSchema() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "AND table_name IN "
                        + "('accounts', 'transactions', 'entries', 'audit_log')",
                Integer.class);

        if (tables == null || tables < 4) {
            throw new IllegalStateException(
                    ("Flyway no migró el esquema: hay %s de 4 tablas. Revisa que "
                            + "org.springframework.boot:spring-boot-flyway esté entre las "
                            + "dependencias de app: en Spring Boot 4, flyway-core por sí "
                            + "solo no activa la autoconfiguración.").formatted(tables));
        }
    }
}

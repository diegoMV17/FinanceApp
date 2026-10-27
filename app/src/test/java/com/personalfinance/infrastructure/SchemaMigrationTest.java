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
import org.springframework.dao.DataAccessException;
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
        jdbc.execute(
                "TRUNCATE entries, transactions, audit_log, budgets, accounts, people CASCADE");

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

            assertThat(applied).contains("esquema inicial", "limites de entrada", "personas");
        }

        @Test
        @DisplayName("P2 · con las seis tablas, la vista y cada uno de sus triggers")
        void everythingIsThere() {
            assertThat(tableNames()).contains(
                    "accounts", "transactions", "entries", "audit_log", "people", "budgets");
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
                    "audit_log_only_grows",
                    "people_keep_their_identity",
                    "people_are_never_deleted",
                    "budgets_keep_their_identity",
                    "budgets_are_never_deleted");
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

    /**
     * Los límites de la V4. Cada uno se prueba en el máximo exacto y en el
     * máximo más uno: un límite probado solo "por encima" pasa igual si el
     * número está mal copiado.
     */
    @Nested
    @DisplayName("acota lo que entra")
    class InputLimits {

        private static final long MAX_CENTS = 1_000_000_000_000L;

        @Test
        @DisplayName("P40 · un apunte en el límite exacto pasa, en los dos signos")
        void theExactLimitGoesThrough() {
            assertThatNoException().isThrownBy(() -> record(
                    "Enorme",
                    new Line(CASH, -MAX_CENTS),
                    new Line(FOOD, MAX_CENTS)));

            assertThat(entryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("P41 · y un centavo más no, en los dos signos")
        void oneCentTooMuch() {
            assertThatThrownBy(() -> record(
                    "Imposible",
                    new Line(CASH, -(MAX_CENTS + 1)),
                    new Line(FOOD, MAX_CENTS + 1)))
                    .hasStackTraceContaining("entries_amount_within_range");

            assertThatThrownBy(() -> record(
                    "Imposible",
                    new Line(CASH, MAX_CENTS + 1),
                    new Line(FOOD, -(MAX_CENTS + 1))))
                    .hasStackTraceContaining("entries_amount_within_range");

            assertThat(entryCount()).isZero();
        }

        @Test
        @DisplayName("P42 · una descripción de doscientos caracteres pasa y una de 201 no")
        void theDescriptionLimit() {
            assertThatNoException().isThrownBy(() -> lunchDescribed("a".repeat(200)));

            assertThatThrownBy(() -> lunchDescribed("a".repeat(201)))
                    .hasStackTraceContaining("transactions_description_within_length");
        }

        @Test
        @DisplayName("P43 · y una con un salto de línea o un tabulador tampoco")
        void theDescriptionIsOneLine() {
            assertThatThrownBy(() -> lunchDescribed("Almuerzo\nSaldo: 0"))
                    .hasStackTraceContaining("transactions_description_is_one_line");

            assertThatThrownBy(() -> lunchDescribed("Almuerzo\tcolumna"))
                    .hasStackTraceContaining("transactions_description_is_one_line");
        }

        /**
         * El NUL no llega al CHECK: Postgres rechaza el byte 0x00 dentro de un
         * texto antes de evaluar cualquier restricción ("invalid byte sequence
         * for encoding UTF8"). Queda rechazado igual, pero por otro camino, y
         * por eso este caso no comprueba el nombre de la restricción.
         */
        @Test
        @DisplayName("P43b · y un NUL lo rechaza la codificación, antes del CHECK")
        void aNulByteNeverGetsIn() {
            assertThatThrownBy(() -> lunchDescribed("Almuerzo\u0000con NUL"))
                    .isInstanceOf(DataAccessException.class);

            assertThat(entryCount()).isZero();
        }

        @Test
        @DisplayName("P44 · el motivo de una anulación se mide igual")
        void theCancellationNoteLimits() {
            assertThatNoException().isThrownBy(() -> cancelWithNote(lunch(), "a".repeat(200)));

            assertThatThrownBy(() -> cancelWithNote(lunch(), "a".repeat(201)))
                    .hasStackTraceContaining("transactions_cancellation_note_within_length");

            assertThatThrownBy(() -> cancelWithNote(lunch(), "Duplicada\nAnulada"))
                    .hasStackTraceContaining("transactions_cancellation_note_is_one_line");
        }

        @Test
        @DisplayName("P45 · y el nombre de una cuenta, hasta sesenta y en una sola línea")
        void theAccountNameLimits() {
            assertThatNoException()
                    .isThrownBy(() -> openAccount(newId(), "ASSET", "a".repeat(60)));

            assertThatThrownBy(() -> openAccount(newId(), "ASSET", "b".repeat(61)))
                    .hasStackTraceContaining("accounts_name_within_length");

            assertThatThrownBy(() -> openAccount(newId(), "ASSET", "Banco\nFalso"))
                    .hasStackTraceContaining("accounts_name_is_one_line");
        }

        /**
         * Lo que hace que el límite de Java y el de Postgres sean el mismo
         * límite: los dos cuentan code points. Si uno contara bytes, un nombre
         * de veinte emojis pasaría en Java y la base lo rechazaría.
         */
        @Test
        @DisplayName("P46 · contando un emoji como un carácter, igual que el dominio")
        void lengthIsCountedInCodePoints() {
            assertThatNoException().isThrownBy(
                    () -> openAccount(newId(), "ASSET", "🏦".repeat(60)));

            assertThatThrownBy(
                    () -> openAccount(newId(), "ASSET", "🏦".repeat(61)))
                    .hasStackTraceContaining("accounts_name_within_length");
        }

        /**
         * El siglo que cubre el libro, replicado aquí.
         *
         * <p>Dueña de la regla: {@code Transaction.EARLIEST_DATE} y
         * {@code LATEST_DATE} (dominio). Una fila con el año 999999999 se
         * quedaría al final de todas las consultas «a fecha» para siempre, y
         * como aquí nada se borra, solo se podría anular.
         */
        @Test
        @DisplayName("P47 · una fecha fuera del siglo del libro no entra")
        void theDateRange() {
            assertThatNoException().isThrownBy(() -> lunchOn("2000-01-01"));
            assertThatNoException().isThrownBy(() -> lunchOn("2099-12-31"));

            assertThatThrownBy(() -> lunchOn("1999-12-31"))
                    .hasStackTraceContaining("transactions_date_within_range");

            assertThatThrownBy(() -> lunchOn("2100-01-01"))
                    .hasStackTraceContaining("transactions_date_within_range");

            assertThatThrownBy(() -> lunchOn("0001-01-01"))
                    .hasStackTraceContaining("transactions_date_within_range");
        }

        private void lunchOn(String occurredOn) {
            recordOn(newId(), occurredOn, "Almuerzo",
                    new Line(CASH, -3_500_000), new Line(FOOD, 3_500_000));
        }

        private void lunchDescribed(String description) {
            recordOn(newId(), SOME_DAY, description,
                    new Line(CASH, -3_500_000), new Line(FOOD, 3_500_000));
        }
    }

    /**
     * Las personas, que son la V6. La columna {@code person_id} estaba desde la
     * V1 con un CHECK y un comentario; lo que faltaba era la tabla a la que
     * apuntaba y la clave ajena.
     */
    @Nested
    @DisplayName("protege a las personas")
    class People {

        private static final String JUAN = "44444444-4444-4444-4444-444444444444";
        private static final String PEDRO = "55555555-5555-5555-5555-555555555555";

        private String owedByJuan;

        @BeforeEach
        void twoPeopleAndAnAccount() {
            addPerson(JUAN, "Juan");
            addPerson(PEDRO, "Pedro");
            owedByJuan = newId();
            openAccount(owedByJuan, "RECEIVABLE", "Juan");
        }

        @Test
        @DisplayName("P48 · dos personas abiertas no se llaman igual")
        void noDuplicateNamesAmongOpenPeople() {
            assertThatThrownBy(() -> addPerson(newId(), "  juan  "))
                    .hasStackTraceContaining("people_open_name_unique");
        }

        @Test
        @DisplayName("P49 · y una archivada libera su nombre")
        void archivingFreesTheName() {
            jdbc.update("UPDATE people SET archived_at = now() WHERE id = ?::uuid", JUAN);

            assertThatNoException().isThrownBy(() -> addPerson(newId(), "Juan"));
        }

        @Test
        @DisplayName("P50 · un nombre en blanco, de sesenta y uno o con salto de línea no pasa")
        void theNameLimits() {
            assertThatNoException().isThrownBy(() -> addPerson(newId(), "a".repeat(60)));

            assertThatThrownBy(() -> addPerson(newId(), "   "))
                    .hasStackTraceContaining("people_name_not_blank");

            assertThatThrownBy(() -> addPerson(newId(), "b".repeat(61)))
                    .hasStackTraceContaining("people_name_within_length");

            assertThatThrownBy(() -> addPerson(newId(), "Juan\nPérez"))
                    .hasStackTraceContaining("people_name_is_one_line");
        }

        /**
         * Lo que hace que el límite de Java y el de Postgres sean el mismo
         * límite: los dos cuentan code points.
         */
        @Test
        @DisplayName("P51 · contando un emoji como un carácter, igual que el dominio")
        void lengthIsCountedInCodePoints() {
            assertThatNoException().isThrownBy(() -> addPerson(newId(), "🙂".repeat(60)));

            assertThatThrownBy(() -> addPerson(newId(), "🙂".repeat(61)))
                    .hasStackTraceContaining("people_name_within_length");
        }

        @Test
        @DisplayName("P52 · una persona no se borra")
        void peopleAreNeverDeleted() {
            assertThatThrownBy(() -> jdbc.update("DELETE FROM people WHERE id = ?::uuid", PEDRO))
                    .hasStackTraceContaining("no se borran");
        }

        @Test
        @DisplayName("P53 · ni se desarchiva")
        void archivingIsFinal() {
            jdbc.update("UPDATE people SET archived_at = now() WHERE id = ?::uuid", JUAN);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE people SET archived_at = NULL WHERE id = ?::uuid", JUAN))
                    .hasStackTraceContaining("ya fue archivada");
        }

        @Test
        @DisplayName("P54 · pero sí se puede renombrar")
        void renamingIsAllowed() {
            assertThatNoException().isThrownBy(() -> jdbc.update(
                    "UPDATE people SET name = 'Juan Pérez' WHERE id = ?::uuid", JUAN));
        }

        /**
         * Sin esta clave ajena, una cuenta podía apuntar a una persona que no
         * existe: el saldo seguía bien y esa cuenta desaparecía de toda vista de
         * personas sin que nada se quejara.
         */
        @Test
        @DisplayName("P55 · una cuenta solo se vincula a una persona que existe")
        void theLinkPointsSomewhere() {
            assertThatThrownBy(() -> linkAccount(owedByJuan, newId()))
                    .hasStackTraceContaining("accounts_person_exists");
        }

        @Test
        @DisplayName("P56 · el vínculo se mueve a otra persona")
        void theLinkMoves() {
            linkAccount(owedByJuan, JUAN);
            linkAccount(owedByJuan, PEDRO);

            assertThat(personOf(owedByJuan)).isEqualTo(PEDRO);
        }

        @Test
        @DisplayName("P57 · pero no se quita")
        void theLinkIsNeverRemoved() {
            linkAccount(owedByJuan, JUAN);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE accounts SET person_id = NULL WHERE id = ?::uuid", owedByJuan))
                    .hasStackTraceContaining("no se quita");

            assertThat(personOf(owedByJuan)).isEqualTo(JUAN);
        }

        @Test
        @DisplayName("P58 · y la bitácora admite la acción de vincular")
        void theAuditKnowsAboutLinking() {
            assertThatNoException().isThrownBy(() -> jdbc.update(
                    "INSERT INTO audit_log (entity, entity_id, action, source) "
                            + "VALUES ('account', ?::uuid, 'LINK', 'APP')", owedByJuan));

            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO audit_log (entity, entity_id, action, source) "
                            + "VALUES ('account', ?::uuid, 'INVENTADA', 'APP')", owedByJuan))
                    .hasStackTraceContaining("audit_known_action");
        }

        private String personOf(String accountId) {
            return jdbc.queryForObject(
                    "SELECT person_id::text FROM accounts WHERE id = ?::uuid",
                    String.class, accountId);
        }
    }

    /**
     * P59 a P66. La V7: un presupuesto es un hecho, y la red de SQL que lo
     * sostiene.
     */
    @Nested
    @DisplayName("los presupuestos")
    class TheBudgets {

        private static final String MARCH = "2026-03-01";

        @Test
        @DisplayName("P59 · un solo presupuesto vivo por categoría y mes")
        void onlyOneLivePerCategoryAndMonth() {
            String first = setBudget(FOOD, MARCH, 50_000_000);

            assertThatThrownBy(() -> setBudget(FOOD, MARCH, 60_000_000))
                    .hasStackTraceContaining("budgets_one_live_per_category_and_month");

            // Y en cuanto el primero deja de contar, el sitio queda libre.
            jdbc.update("UPDATE budgets SET superseded_at = now() WHERE id = ?::uuid", first);
            assertThatNoException().isThrownBy(() -> setBudget(FOOD, MARCH, 60_000_000));
        }

        @Test
        @DisplayName("P60 · dos categorías o dos meses no chocan")
        void differentCategoriesOrMonthsDoNotCollide() {
            setBudget(FOOD, MARCH, 50_000_000);

            assertThatNoException().isThrownBy(() -> setBudget(CASH, MARCH, 10_000_000));
            assertThatNoException().isThrownBy(() -> setBudget(FOOD, "2026-04-01", 50_000_000));
        }

        @Test
        @DisplayName("P61 · el mes es el día 1, y está dentro del siglo del libro")
        void thePeriodIsAWholeMonthInRange() {
            assertThatThrownBy(() -> setBudget(FOOD, "2026-03-15", 50_000_000))
                    .hasStackTraceContaining("budgets_period_is_a_whole_month");

            assertThatThrownBy(() -> setBudget(FOOD, "1999-12-01", 50_000_000))
                    .hasStackTraceContaining("budgets_period_in_range");

            assertThatThrownBy(() -> setBudget(FOOD, "2100-01-01", 50_000_000))
                    .hasStackTraceContaining("budgets_period_in_range");
        }

        @Test
        @DisplayName("P62 · el límite es positivo y tiene techo")
        void theLimitIsPositiveAndBounded() {
            assertThatThrownBy(() -> setBudget(FOOD, MARCH, 0))
                    .hasStackTraceContaining("budgets_limit_is_positive");

            assertThatThrownBy(() -> setBudget(FOOD, MARCH, -1))
                    .hasStackTraceContaining("budgets_limit_is_positive");

            assertThatNoException().isThrownBy(
                    () -> setBudget(FOOD, "2026-05-01", 1_000_000_000_000L));

            assertThatThrownBy(() -> setBudget(FOOD, MARCH, 1_000_000_000_001L))
                    .hasStackTraceContaining("budgets_limit_within_range");
        }

        @Test
        @DisplayName("P63 · la categoría tiene que existir")
        void theCategoryHasToExist() {
            assertThatThrownBy(() -> setBudget(newId(), MARCH, 50_000_000))
                    .hasStackTraceContaining("budgets_category_exists");
        }

        @Test
        @DisplayName("P64 · un presupuesto no se edita")
        void aBudgetIsNeverEdited() {
            String id = setBudget(FOOD, MARCH, 50_000_000);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE budgets SET limit_cents = 99 WHERE id = ?::uuid", id))
                    .hasStackTraceContaining("no se edita");

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE budgets SET period = ?::date WHERE id = ?::uuid", "2026-04-01", id))
                    .hasStackTraceContaining("no se edita");

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE budgets SET category_id = ?::uuid WHERE id = ?::uuid", CASH, id))
                    .hasStackTraceContaining("no se edita");
        }

        @Test
        @DisplayName("P65 · y lo que dejó de contar no vuelve")
        void supersedingIsFinal() {
            String id = setBudget(FOOD, MARCH, 50_000_000);
            jdbc.update("UPDATE budgets SET superseded_at = now() WHERE id = ?::uuid", id);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE budgets SET superseded_at = NULL WHERE id = ?::uuid", id))
                    .hasStackTraceContaining("dejó de contar");
        }

        @Test
        @DisplayName("P66 · ni se borra, y cada uno es reemplazado por uno solo")
        void neverDeletedAndReplacedOnce() {
            String first = setBudget(FOOD, MARCH, 50_000_000);

            assertThatThrownBy(() -> jdbc.update(
                    "DELETE FROM budgets WHERE id = ?::uuid", first))
                    .hasStackTraceContaining("no se borran");

            jdbc.update("UPDATE budgets SET superseded_at = now() WHERE id = ?::uuid", first);
            replaceBudget(newId(), FOOD, MARCH, 60_000_000, first);

            assertThatThrownBy(() -> replaceBudget(
                    newId(), FOOD, "2026-04-01", 60_000_000, first))
                    .hasStackTraceContaining("budgets_one_replacement_each");
        }

        private String setBudget(String categoryId, String period, long cents) {
            String id = newId();
            jdbc.update("INSERT INTO budgets (id, category_id, period, limit_cents) "
                            + "VALUES (?::uuid, ?::uuid, ?::date, ?)",
                    id, categoryId, period, cents);
            return id;
        }

        private void replaceBudget(
                String id, String categoryId, String period, long cents, String replaced) {

            jdbc.update("INSERT INTO budgets "
                            + "(id, category_id, period, limit_cents, replaces_id) "
                            + "VALUES (?::uuid, ?::uuid, ?::date, ?, ?::uuid)",
                    id, categoryId, period, cents, replaced);
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

    private void addPerson(String id, String name) {
        jdbc.update("INSERT INTO people (id, name) VALUES (?::uuid, ?)", id, name);
    }

    private void linkAccount(String accountId, String personId) {
        jdbc.update("UPDATE accounts SET person_id = ?::uuid WHERE id = ?::uuid",
                personId, accountId);
    }

    private void cancelWithNote(String id, String note) {
        jdbc.update("UPDATE transactions SET cancelled_at = now(), "
                + "cancellation_source = 'APP', cancellation_note = ? WHERE id = ?::uuid",
                note, id);
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

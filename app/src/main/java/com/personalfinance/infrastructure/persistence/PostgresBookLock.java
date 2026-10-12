package com.personalfinance.infrastructure.persistence;

import com.personalfinance.application.BookBusyException;
import com.personalfinance.application.port.BookLock;
import java.sql.SQLException;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * El turno para escribir, con un candado consultivo de Postgres.
 *
 * <p>{@code pg_advisory_xact_lock} se suelta solo, al terminar la transacción,
 * haga commit o rollback. No hay un {@code release} que a alguien se le pueda
 * olvidar llamar, y una excepción a mitad de un caso de uso no deja el libro
 * bloqueado.
 *
 * <p>Por eso mismo exige una transacción abierta: en autocommit el candado se
 * soltaría al terminar la sentencia que lo pide, y las siguientes correrían sin
 * protección sin que nada lo delate. Si no hay transacción, esto falla en vez de
 * fingir que protege algo.
 *
 * <p>La espera máxima se aplica con {@code set_config} y un parámetro, no con
 * {@code SET LOCAL}: un {@code SET} no acepta parámetros, así que la única
 * alternativa sería interpolar el número dentro del texto de la sentencia, y en
 * este proyecto no se arma SQL concatenando. El tercer argumento en
 * {@code true} ata el valor a esta transacción, así que la conexión vuelve al
 * pool como estaba.
 *
 * <p>Que {@code lock_timeout} cubra los candados consultivos está comprobado
 * contra Postgres 16, no supuesto: lo prueba C4.
 */
@Component
class PostgresBookLock implements BookLock {

    /**
     * La clave del candado: "FINANCE" en ASCII. Es un número arbitrario, pero
     * uno así es reconocible en {@code pg_locks} y no choca por casualidad con
     * el de otra herramienta que use candados consultivos en esta base.
     */
    private static final long THE_BOOK = 0x46_49_4E_41_4E_43_45L;

    /** {@code lock_not_available}: el estado con que Postgres corta por lock_timeout. */
    private static final String LOCK_TIMEOUT = "55P03";

    private final JdbcClient jdbc;
    private final Duration timeout;

    PostgresBookLock(JdbcClient jdbc, BookLockProperties properties) {
        this.jdbc = jdbc;
        this.timeout = properties.timeout();
    }

    @Override
    public void acquire() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "El candado del libro solo tiene sentido dentro de una transacción: "
                            + "en autocommit se suelta al terminar la sentencia y no "
                            + "protegería nada");
        }

        jdbc.sql("SELECT set_config('lock_timeout', :milliseconds, true)")
                .param("milliseconds", String.valueOf(timeout.toMillis()))
                .query(String.class)
                .single();

        try {
            jdbc.sql("SELECT pg_advisory_xact_lock(:key)")
                    .param("key", THE_BOOK)
                    .query()
                    .singleRow();
        } catch (DataAccessException failed) {
            if (timedOut(failed)) {
                throw new BookBusyException(timeout);
            }
            throw failed;
        }
    }

    private static boolean timedOut(DataAccessException failed) {
        return failed.getMostSpecificCause() instanceof SQLException cause
                && LOCK_TIMEOUT.equals(cause.getSQLState());
    }
}

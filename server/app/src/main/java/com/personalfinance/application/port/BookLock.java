package com.personalfinance.application.port;

/**
 * El turno para escribir en el libro.
 *
 * <p>Todo caso de uso que escriba lo toma como primera sentencia de su
 * transacción y lo suelta al terminarla, haga commit o rollback. El que llega
 * segundo espera y después se ejecuta normalmente: no se rechaza nada y no hay
 * nada que reintentar.
 *
 * <p>Hace falta aunque la aplicación sea de un solo usuario, porque hay reglas
 * que cruzan filas y que la base no ve. Archivar una cuenta exige que su saldo
 * eventual sea cero: si mientras se decide eso otra petición registra un
 * movimiento en esa cuenta, el resultado es una cuenta archivada con plata
 * adentro, y archivar no se deshace. Y el caso más probable no es ni ese: es el
 * cliente reintentando por un timeout mientras la primera petición todavía
 * corre.
 *
 * <p>Un solo candado para todo el libro, no uno por cuenta: un usuario y
 * escrituras de milisegundos. Más fino sería más complejo sin beneficio.
 */
public interface BookLock {

    /**
     * Espera el turno y lo toma hasta el final de la transacción en curso.
     *
     * @throws IllegalStateException   si no hay una transacción activa; un
     *                                 candado tomado en autocommit se suelta al
     *                                 terminar la sentencia y no protegería
     *                                 nada, en silencio
     * @throws com.personalfinance.application.BookBusyException
     *                                 si la espera se vence sin haber escrito
     *                                 nada; es seguro reintentar
     */
    void acquire();
}

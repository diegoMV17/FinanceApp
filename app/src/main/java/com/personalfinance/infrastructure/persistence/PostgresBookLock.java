package com.personalfinance.infrastructure.persistence;

import com.personalfinance.application.port.BookLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** El turno para escribir, con un candado consultivo de Postgres. */
@Component
class PostgresBookLock implements BookLock {

    private final JdbcClient jdbc;
    private final BookLockProperties properties;

    PostgresBookLock(JdbcClient jdbc, BookLockProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public void acquire() {
        throw new UnsupportedOperationException("paso 4");
    }
}

package com.personalfinance.infrastructure.persistence;

import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** La bitácora, contra la tabla {@code audit_log}. */
@Repository
class PostgresAuditTrail implements AuditTrail {

    private final JdbcClient jdbc;

    PostgresAuditTrail(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditRecord record) {
        throw new UnsupportedOperationException("paso 4");
    }
}

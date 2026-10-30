package com.personalfinance.infrastructure.persistence;

import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import java.sql.Types;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * La bitácora, contra la tabla {@code audit_log}.
 *
 * <p>No es una entidad JPA: el id de esa tabla es {@code BIGSERIAL}, lo pone la
 * base, así que no puede extender {@link AssignedIdRow} y tampoco gana nada
 * pasando por Hibernate. Un INSERT con parámetros con nombre y se acabó.
 *
 * <p>{@code MANDATORY}: la fila de auditoría va en la misma transacción que el
 * cambio que describe. Si se guardara aparte, un fallo entre las dos dejaría un
 * cambio sin rastro o un rastro de algo que no pasó.
 *
 * <p>El JSON lo arma Jackson y entra como parámetro, nunca concatenado: lo que
 * va ahí son nombres de cuentas que el usuario escribió, y un nombre con una
 * comilla no puede terminar de ser sintaxis.
 *
 * <p>El mapeador se construye aquí en vez de inyectar el de la aplicación a
 * propósito: lo que queda escrito en una bitácora que solo crece no debería
 * cambiar de forma porque alguien ajuste la configuración global de Jackson
 * para la API.
 */
@Repository
class PostgresAuditTrail implements AuditTrail {

    private static final String INSERT = """
            INSERT INTO audit_log (entity, entity_id, action, before, after, source)
            VALUES (:entity, :entityId, :action,
                    CAST(:before AS jsonb), CAST(:after AS jsonb),
                    CAST(:source AS transaction_source))
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    PostgresAuditTrail(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditRecord entry) {
        jdbc.sql(INSERT)
                .param("entity", entry.entity().storedAs())
                .param("entityId", entry.entityId())
                .param("action", entry.action().name())
                .param("before", asJson(entry.before()), Types.VARCHAR)
                .param("after", asJson(entry.after()), Types.VARCHAR)
                .param("source", entry.source().name())
                .update();
    }

    /** Nada que guardar se guarda como NULL, no como un objeto vacío. */
    private static String asJson(Map<String, String> values) {
        return values.isEmpty() ? null : JSON.writeValueAsString(values);
    }
}

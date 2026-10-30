-- Lo que la V1 prometía en sus comentarios y no terminaba de cumplir.
--
-- La V1 protegía los apuntes y la fecha de una anulación, pero dejaba abierto
-- casi todo lo demás. Con un UPDATE se podía, sin dejar rastro:
--
--   * mover un movimiento a otra fecha (y con él, los saldos de ese mes),
--   * reescribir el motivo o el origen de una anulación ya hecha,
--   * cambiarle el tipo a una cuenta, desarchivarla o borrarla,
--   * editar o borrar la auditoría,
--   * colgarle a una transacción ya guardada una segunda copia de sus apuntes,
--     que el trigger de balance no detecta porque cero más cero es cero.
--
-- Esta migración cierra esas puertas. No edita la V1: una migración aplicada
-- no se toca; se reemplaza lo necesario aquí.


-- ---------------------------------------------------------------------------
-- Una transacción guardada solo admite un cambio: que la anulen, una vez.
--
-- Reemplaza al trigger de la V1, que solo cuidaba cancelled_at: con él, la
-- fecha de la anulación quedaba fija pero su motivo y su origen se podían
-- reescribir, y el encabezado entero estaba abierto.
--
-- updated_at queda fuera a propósito: es el marcador que usará la
-- sincronización para saber qué cambió.
-- ---------------------------------------------------------------------------
DROP TRIGGER transactions_cancelled_once ON transactions;
DROP FUNCTION refuse_recancellation();

CREATE FUNCTION refuse_transaction_rewrite() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id          IS DISTINCT FROM OLD.id
    OR NEW.occurred_on IS DISTINCT FROM OLD.occurred_on
    OR NEW.description IS DISTINCT FROM OLD.description
    OR NEW.source      IS DISTINCT FROM OLD.source
    OR NEW.replaces_id IS DISTINCT FROM OLD.replaces_id
    OR NEW.recorded_at IS DISTINCT FROM OLD.recorded_at
    OR NEW.device_id   IS DISTINCT FROM OLD.device_id
    THEN
        RAISE EXCEPTION
            'El encabezado de la transacción % no se modifica. Anúlala y registra una corrección.',
            OLD.id;
    END IF;

    IF OLD.cancelled_at IS NOT NULL AND (
           NEW.cancelled_at        IS DISTINCT FROM OLD.cancelled_at
        OR NEW.cancellation_note   IS DISTINCT FROM OLD.cancellation_note
        OR NEW.cancellation_source IS DISTINCT FROM OLD.cancellation_source)
    THEN
        RAISE EXCEPTION
            'La transacción % ya fue anulada el %; esa anulación no se reescribe',
            OLD.id, OLD.cancelled_at;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transactions_only_cancel_once
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION refuse_transaction_rewrite();


-- ---------------------------------------------------------------------------
-- Una cuenta no cambia de tipo ni de identidad, no se desarchiva y no se borra.
--
-- Toda la historia de una cuenta se lee según su tipo: cambiar un ASSET a
-- EXPENSE convertiría de un golpe cada depósito en un gasto. Y "archivada el
-- día X" es un hecho; deshacerlo o moverlo de fecha reescribe la historia.
--
-- Nombre, ícono, color y persona sí pueden cambiar: son presentación.
-- ---------------------------------------------------------------------------
CREATE FUNCTION refuse_account_rewrite() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id         IS DISTINCT FROM OLD.id
    OR NEW.type       IS DISTINCT FROM OLD.type
    OR NEW.created_at IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION
            'La cuenta % no cambia de tipo ni de identidad: su historia se lee según ellos. Archívala y abre otra.',
            OLD.id;
    END IF;

    IF OLD.archived_at IS NOT NULL
       AND NEW.archived_at IS DISTINCT FROM OLD.archived_at
    THEN
        RAISE EXCEPTION
            'La cuenta % ya fue archivada el %; el archivado no se deshace ni se reescribe',
            OLD.id, OLD.archived_at;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER accounts_keep_their_identity
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION refuse_account_rewrite();

CREATE FUNCTION refuse_account_delete() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Las cuentas no se borran. Archívala: su historia la sigue necesitando.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER accounts_are_never_deleted
    BEFORE DELETE ON accounts
    FOR EACH ROW EXECUTE FUNCTION refuse_account_delete();


-- ---------------------------------------------------------------------------
-- La auditoría solo crece.
--
-- Una bitácora que se puede editar no prueba nada: quien quiera ocultar un
-- movimiento empezaría justamente por aquí.
-- ---------------------------------------------------------------------------
CREATE FUNCTION refuse_audit_change() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'La auditoría solo admite agregar registros; no se edita ni se borra.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_only_grows
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION refuse_audit_change();


-- ---------------------------------------------------------------------------
-- Cada apunte ocupa una posición dentro de su transacción, y solo una vez.
--
-- Además de ordenar, cierra la puerta a duplicar los apuntes de una
-- transacción ya guardada: la segunda copia chocaría en (transaction_id, 0).
-- Sin valor por defecto: quien inserte un apunte tiene que decir cuál es, en
-- vez de que todos caigan en la posición 0 y el error salga más adelante.
--
-- El índice nuevo empieza por transaction_id, así que también sirve para lo
-- que hacía entries_by_transaction, que ya sobra.
-- ---------------------------------------------------------------------------
ALTER TABLE entries ALTER COLUMN position DROP DEFAULT;

CREATE UNIQUE INDEX entries_one_per_position ON entries (transaction_id, position);

DROP INDEX entries_by_transaction;

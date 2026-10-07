-- Núcleo contable: cuentas, transacciones y apuntes.
--
-- Las reglas de negocio viven en el dominio Java, que es donde dan mensajes
-- útiles y se prueban en milisegundos. Lo que hay aquí es la red de seguridad:
-- lo que impide que un script, una migración futura o un psql abierto a las dos
-- de la mañana dejen el libro en un estado que el dominio nunca habría permitido.

CREATE TYPE account_type AS ENUM (
    'ASSET',
    'RECEIVABLE',
    'PAYABLE',
    'THIRD_PARTY_FUND',
    'INCOME',
    'EXPENSE',
    'EQUITY'
);

CREATE TYPE transaction_source AS ENUM ('APP', 'BOT', 'RECURRING', 'SYSTEM');


-- ---------------------------------------------------------------------------
-- Cuentas: bancos, personas, fondos de terceros y categorías. Nunca se borran.
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id            UUID PRIMARY KEY,
    type          account_type NOT NULL,
    name          TEXT         NOT NULL,
    icon          TEXT,
    color         TEXT,
    person_id     UUID,                      -- incremento 2
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT accounts_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT accounts_person_only_on_person_types CHECK (
        person_id IS NULL
        OR type IN ('RECEIVABLE', 'PAYABLE', 'THIRD_PARTY_FUND')
    )
);

-- Dos cuentas abiertas del mismo tipo no pueden llamarse igual; una archivada sí
-- puede repetir el nombre de una viva. Es la misma regla que Chart aplica en Java.
CREATE UNIQUE INDEX accounts_open_name_unique
    ON accounts (type, lower(btrim(name)))
    WHERE archived_at IS NULL;

CREATE INDEX accounts_open ON accounts (type) WHERE archived_at IS NULL;


-- ---------------------------------------------------------------------------
-- Transacciones: solo el encabezado. Ningún monto vive aquí, porque un monto
-- guardado aparte puede alejarse de la suma de sus apuntes, y entonces hay dos
-- respuestas para la misma pregunta.
-- ---------------------------------------------------------------------------
CREATE TABLE transactions (
    id                UUID PRIMARY KEY,
    occurred_on       DATE               NOT NULL,   -- cuándo pasó
    description       TEXT               NOT NULL DEFAULT '',
    source            transaction_source NOT NULL DEFAULT 'APP',

    cancelled_at      TIMESTAMPTZ,
    cancellation_note TEXT,
    replaces_id       UUID REFERENCES transactions (id),

    recorded_at       TIMESTAMPTZ        NOT NULL DEFAULT now(),  -- cuándo lo capturaste
    device_id         UUID,
    updated_at        TIMESTAMPTZ        NOT NULL DEFAULT now(),

    CONSTRAINT transactions_not_self_replacing CHECK (replaces_id IS DISTINCT FROM id)
);

-- Una transacción corrige a lo sumo a otra: dos correcciones de la misma versión
-- dejarían dos verdades simultáneas y la cadena de revisiones se bifurcaría.
CREATE UNIQUE INDEX transactions_one_replacement_each
    ON transactions (replaces_id)
    WHERE replaces_id IS NOT NULL;

CREATE INDEX transactions_live_by_date
    ON transactions (occurred_on DESC)
    WHERE cancelled_at IS NULL;


-- ---------------------------------------------------------------------------
-- Apuntes: el corazón. Inmutables: solo INSERT.
-- El dinero se guarda en centavos como BIGINT. Nunca en punto flotante.
-- ---------------------------------------------------------------------------
CREATE TABLE entries (
    id             UUID     PRIMARY KEY,
    transaction_id UUID     NOT NULL REFERENCES transactions (id),
    account_id     UUID     NOT NULL REFERENCES accounts (id),
    amount_cents   BIGINT   NOT NULL,
    position       SMALLINT NOT NULL DEFAULT 0,

    CONSTRAINT entries_amount_not_zero CHECK (amount_cents <> 0)
);

-- El índice que sostiene todo saldo: SUM(amount_cents) por cuenta.
CREATE INDEX entries_by_account ON entries (account_id);
CREATE INDEX entries_by_transaction ON entries (transaction_id);


-- ---------------------------------------------------------------------------
-- Auditoría: append-only. El campo source es el que importa cuando el bot
-- interpreta mal un mensaje y hay que saber de dónde salió ese movimiento raro.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity      TEXT               NOT NULL,
    entity_id   UUID               NOT NULL,
    action      TEXT               NOT NULL,
    before      JSONB,
    after       JSONB,
    source      transaction_source NOT NULL,
    device_id   UUID,
    occurred_at TIMESTAMPTZ        NOT NULL DEFAULT now(),

    CONSTRAINT audit_known_action CHECK (
        action IN ('CREATE', 'AMEND', 'CANCEL', 'ARCHIVE', 'RENAME')
    )
);

CREATE INDEX audit_by_entity ON audit_log (entity, entity_id, occurred_at DESC);


-- ---------------------------------------------------------------------------
-- La invariante del libro: los apuntes de una transacción suman cero.
--
-- Un CHECK no sirve: la regla cruza filas. Hace falta un constraint trigger
-- DEFERRABLE INITIALLY DEFERRED, que corre al cerrar la transacción de base de
-- datos, cuando ya están insertados todos los apuntes. Sin el DEFERRED se
-- dispararía tras el primer apunte y fallaría siempre.
-- ---------------------------------------------------------------------------
CREATE FUNCTION assert_entries_balance() RETURNS TRIGGER AS $$
DECLARE
    total     BIGINT;
    how_many  INTEGER;
BEGIN
    SELECT COALESCE(SUM(amount_cents), 0), COUNT(*)
      INTO total, how_many
      FROM entries
     WHERE transaction_id = NEW.transaction_id;

    -- La transacción pudo haber sido borrada en la misma transacción de BD.
    IF how_many = 0 THEN
        RETURN NULL;
    END IF;

    IF how_many < 2 THEN
        RAISE EXCEPTION
            'La transacción % tiene % apunte(s); el mínimo es 2',
            NEW.transaction_id, how_many;
    END IF;

    IF total <> 0 THEN
        RAISE EXCEPTION
            'La transacción % no balancea: los apuntes suman %',
            NEW.transaction_id, total;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER entries_must_balance
    AFTER INSERT ON entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_entries_balance();


-- ---------------------------------------------------------------------------
-- Inmutabilidad de los apuntes.
--
-- Que el código decida no hacer UPDATE no es lo mismo que que no se pueda.
-- El mensaje enseña qué hacer en vez de solo prohibir.
-- ---------------------------------------------------------------------------
CREATE FUNCTION refuse_entry_change() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Los apuntes son inmutables. Anula la transacción y registra una nueva.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER entries_are_immutable
    BEFORE UPDATE OR DELETE ON entries
    FOR EACH ROW EXECUTE FUNCTION refuse_entry_change();


-- ---------------------------------------------------------------------------
-- Las transacciones tampoco se borran, y una anulación no se sobrescribe.
-- ---------------------------------------------------------------------------
CREATE FUNCTION refuse_transaction_delete() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Las transacciones no se borran. Anúlala: el historial la conserva.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transactions_are_never_deleted
    BEFORE DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION refuse_transaction_delete();

CREATE FUNCTION refuse_recancellation() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.cancelled_at IS NOT NULL AND NEW.cancelled_at IS DISTINCT FROM OLD.cancelled_at THEN
        RAISE EXCEPTION
            'La transacción % ya fue anulada el %; esa fecha no se reescribe',
            OLD.id, OLD.cancelled_at;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transactions_cancelled_once
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION refuse_recancellation();


-- ---------------------------------------------------------------------------
-- Saldo por cuenta contando todo lo registrado, incluidos los movimientos
-- programados a futuro. Es el equivalente en SQL de Ledger.eventualBalanceOf,
-- que es la pregunta que decide si una cuenta se puede archivar.
--
-- El filtro de anuladas va dentro de la subconsulta, no en el ON de un LEFT
-- JOIN: ahí el join simplemente falla, el apunte sobrevive en el SUM y el saldo
-- sale como si nunca se hubiera anulado nada.
--
-- No hay columna con el signo de presentación a propósito. Esa conversión vive
-- en BalanceNature, en Java, y repetirla aquí sería una segunda verdad que
-- alguien tendría que recordar actualizar al agregar un tipo de cuenta.
-- ---------------------------------------------------------------------------
CREATE VIEW account_eventual_balances AS
SELECT a.id   AS account_id,
       a.type AS account_type,
       a.name AS account_name,
       COALESCE(SUM(live.amount_cents), 0) AS ledger_cents
  FROM accounts a
  LEFT JOIN (
      SELECT e.account_id, e.amount_cents
        FROM entries e
        JOIN transactions t ON t.id = e.transaction_id
       WHERE t.cancelled_at IS NULL
  ) live ON live.account_id = a.id
 GROUP BY a.id, a.type, a.name;

-- Las personas: a quién le debes, quién te debe y de quién guardas plata.
--
-- La V1 ya dejó `accounts.person_id` con su CHECK y un comentario que decía
-- "incremento 2". Esto es ese incremento: la tabla a la que apuntaba esa
-- columna, la clave ajena que le faltaba, y las mismas protecciones que ya
-- tienen las cuentas.
--
-- Las reglas viven en el dominio Java, donde dan mensajes útiles y se prueban
-- en milisegundos. Lo que hay aquí es la red de seguridad: lo que impide que un
-- script, una migración futura o un psql abierto a las dos de la mañana dejen
-- el libro en un estado que el dominio nunca habría permitido.
--
-- No se tocan la V1, la V2, la V3, la V4 ni la V5: una migración aplicada no se
-- edita; se reemplaza aquí lo que haga falta.


-- ---------------------------------------------------------------------------
-- La tabla. Nunca se borra una persona: se archiva, igual que una cuenta.
--
-- Dueña de la regla del nombre: Person.MAX_NAME_LENGTH (dominio), que cuenta
-- code points después de recortar — que es justo lo que cuenta char_length. Si
-- cambia una, cambia la otra.
--
-- Y el nombre no admite caracteres de control, por lo mismo que no los admite
-- el de una cuenta: un salto de línea dentro de un nombre es una línea de log
-- falsificada, y este nombre acaba en una pantalla, en un log y en un mensaje
-- de chat.
-- ---------------------------------------------------------------------------
CREATE TABLE people (
    id          UUID PRIMARY KEY,
    name        TEXT        NOT NULL,
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT people_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT people_name_within_length CHECK (char_length(name) <= 60),
    CONSTRAINT people_name_is_one_line CHECK (name !~ '[[:cntrl:]]')
);

-- Dos personas que todavía se pueden elegir no se llaman igual; una archivada sí
-- puede repetir el nombre de una viva. Es la misma regla que Directory aplica en
-- Java, y la misma forma que accounts_open_name_unique.
CREATE UNIQUE INDEX people_open_name_unique
    ON people (lower(btrim(name)))
    WHERE archived_at IS NULL;


-- ---------------------------------------------------------------------------
-- El vínculo: la columna de la V1, por fin con destino.
--
-- Sin esta clave ajena, `person_id` podía apuntar a una persona que no existe y
-- esa cuenta desaparecía de toda vista de personas sin que nada se quejara: el
-- saldo seguía bien y la posición de alguien quedaba incompleta, que es la
-- forma más cara de equivocarse aquí.
-- ---------------------------------------------------------------------------
ALTER TABLE accounts ADD CONSTRAINT accounts_person_exists
    FOREIGN KEY (person_id) REFERENCES people (id);

-- El índice que sostiene la vista: las cuentas de una persona.
CREATE INDEX accounts_by_person ON accounts (person_id) WHERE person_id IS NOT NULL;


-- ---------------------------------------------------------------------------
-- Una persona no cambia de identidad, no se desarchiva y no se borra.
--
-- "Archivada el día X" es un hecho; deshacerlo o moverlo de fecha reescribe la
-- historia. El nombre sí cambia: es presentación.
--
-- updated_at queda fuera a propósito, igual que en transactions y accounts: es
-- el marcador que usará la sincronización para saber qué cambió.
-- ---------------------------------------------------------------------------
CREATE FUNCTION refuse_person_rewrite() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id         IS DISTINCT FROM OLD.id
    OR NEW.created_at IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION
            'La persona % no cambia de identidad. Archívala y escribe otra.',
            OLD.id;
    END IF;

    IF OLD.archived_at IS NOT NULL
       AND NEW.archived_at IS DISTINCT FROM OLD.archived_at
    THEN
        RAISE EXCEPTION
            'La persona % ya fue archivada el %; el archivado no se deshace ni se reescribe',
            OLD.id, OLD.archived_at;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER people_keep_their_identity
    BEFORE UPDATE ON people
    FOR EACH ROW EXECUTE FUNCTION refuse_person_rewrite();

CREATE FUNCTION refuse_person_delete() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Las personas no se borran. Archívala: la historia la sigue necesitando.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER people_are_never_deleted
    BEFORE DELETE ON people
    FOR EACH ROW EXECUTE FUNCTION refuse_person_delete();


-- ---------------------------------------------------------------------------
-- El vínculo de una cuenta se mueve a otra persona, pero no se quita.
--
-- Reemplaza al trigger de la V3, que dejaba `person_id` completamente libre
-- porque entonces no apuntaba a ninguna parte. Hoy sí significa algo, y las dos
-- direcciones no son iguales: equivocarse de persona es un dedazo que se
-- corrige moviéndolo, y para eso la cuenta no tiene que archivarse ni el libro
-- cambiar. Volver a "no es de nadie", en cambio, borra un hecho que alguien ya
-- declaró, en un libro donde nada se borra.
--
-- Dueño de la regla: Account.linkTo (dominio), que directamente no tiene un
-- método para quitar el vínculo. Esto es la red de atrás.
-- ---------------------------------------------------------------------------
DROP TRIGGER accounts_keep_their_identity ON accounts;
DROP FUNCTION refuse_account_rewrite();

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

    IF OLD.person_id IS NOT NULL AND NEW.person_id IS NULL THEN
        RAISE EXCEPTION
            'La cuenta % ya es de alguien; ese vínculo se mueve a otra persona, no se quita',
            OLD.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER accounts_keep_their_identity
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION refuse_account_rewrite();


-- ---------------------------------------------------------------------------
-- Vincular es una acción más de la bitácora.
--
-- No es ninguna de las cinco que había: no se crea nada, no se corrige, no se
-- anula y no se archiva. Y es justo la que hay que poder auditar, porque mover
-- el vínculo cambia de quién es una deuda y la tabla `accounts` solo conserva
-- la persona de ahora. El id anterior va en `before`.
--
-- Dueño de la lista: AuditAction (casos de uso).
-- ---------------------------------------------------------------------------
ALTER TABLE audit_log DROP CONSTRAINT audit_known_action;

ALTER TABLE audit_log ADD CONSTRAINT audit_known_action CHECK (
    action IN ('CREATE', 'AMEND', 'CANCEL', 'ARCHIVE', 'RENAME', 'LINK')
);

-- Los presupuestos: cuánto se piensa gastar en una categoría y en un mes.
--
-- Las reglas viven en el dominio Java, donde dan mensajes útiles y se prueban
-- en milisegundos. Lo que hay aquí es la red de seguridad: lo que impide que un
-- script, una migración futura o un psql abierto a las dos de la mañana dejen
-- el libro en un estado que el dominio nunca habría permitido.
--
-- No se tocan la V1 a la V6: una migración aplicada no se edita; se reemplaza
-- aquí lo que haga falta.
--
-- Nota para el despliegue: el rol de mínimo privilegio de la aplicación
-- necesita SELECT, INSERT y UPDATE sobre esta tabla, igual que sobre las demás.


-- ---------------------------------------------------------------------------
-- La tabla. Un presupuesto es un hecho, y como todo hecho de este libro no se
-- edita ni se borra: poner otro límite registra una fila nueva que apunta a la
-- anterior por `replaces_id`, y la anterior se marca con el momento en que
-- dejó de contar.
--
-- El vínculo se escribe una sola vez, en el reemplazo. No hay `superseded_by`:
-- sería el mismo hecho guardado en las dos direcciones, y una de las dos
-- copias acabaría equivocada. `superseded_at IS NULL` es lo que dice qué
-- límite cuenta hoy, que es la única pregunta que se hace.
--
-- Dueño de las reglas del monto: Budget (dominio), con el mismo tope que un
-- apunte y por la misma razón aritmética (Entry.MAX_ABSOLUTE_CENTS). Si cambia
-- uno, cambia el otro.
--
-- `period` es el día 1 del mes y no un mes suelto, porque un DATE se compara,
-- se ordena y se indexa, y un texto "2026-03" no hace ninguna de las tres.
-- ---------------------------------------------------------------------------
CREATE TABLE budgets (
    id            UUID PRIMARY KEY,
    category_id   UUID        NOT NULL,
    period        DATE        NOT NULL,
    limit_cents   BIGINT      NOT NULL,
    replaces_id   UUID,
    superseded_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT budgets_category_exists
        FOREIGN KEY (category_id) REFERENCES accounts (id),
    CONSTRAINT budgets_replaces_exists
        FOREIGN KEY (replaces_id) REFERENCES budgets (id),

    -- Cero no es un presupuesto de cero: es no tener presupuesto, y las dos
    -- cosas se leen muy distinto en un informe.
    CONSTRAINT budgets_limit_is_positive CHECK (limit_cents > 0),
    CONSTRAINT budgets_limit_within_range CHECK (limit_cents <= 1000000000000),

    -- El día 1, siempre. EXTRACT sobre un DATE es inmutable, que es lo que un
    -- CHECK exige; date_trunc sobre timestamptz no lo sería.
    CONSTRAINT budgets_period_is_a_whole_month CHECK (EXTRACT(DAY FROM period) = 1),

    -- El mismo siglo que un movimiento (V5), por lo mismo: una sola fila en el
    -- año 999999999 se queda al final de todo informe por mes para el resto de
    -- la vida del libro, y aquí nada se borra.
    CONSTRAINT budgets_period_in_range CHECK (
        period BETWEEN DATE '2000-01-01' AND DATE '2099-12-31'
    ),

    -- Un presupuesto no se reemplaza a sí mismo.
    CONSTRAINT budgets_replaces_another CHECK (replaces_id IS DISTINCT FROM id)
);


-- ---------------------------------------------------------------------------
-- Un solo límite vivo por categoría y por mes.
--
-- Es la invariante que sostiene toda la vista: con dos vivos, "cuánto llevo
-- del presupuesto de Alimentación en marzo" tendría dos respuestas y las dos
-- serían defendibles. La misma forma que accounts_open_name_unique y
-- people_open_name_unique: expresión normalizada más un WHERE que excluye lo
-- que ya no cuenta.
-- ---------------------------------------------------------------------------
CREATE UNIQUE INDEX budgets_one_live_per_category_and_month
    ON budgets (category_id, period)
    WHERE superseded_at IS NULL;

-- Y cada límite es reemplazado por uno solo, igual que una transacción
-- corregida. Sin esto, dos filas podrían decir que reemplazan a la misma y la
-- historia se bifurcaría.
CREATE UNIQUE INDEX budgets_one_replacement_each
    ON budgets (replaces_id)
    WHERE replaces_id IS NOT NULL;

-- El índice que sostiene la lectura del mes.
CREATE INDEX budgets_by_period ON budgets (period) WHERE superseded_at IS NULL;


-- ---------------------------------------------------------------------------
-- Un presupuesto no cambia de identidad, no revive y no se borra.
--
-- Ni la categoría ni el mes ni el monto: los tres son lo que alguien declaró, y
-- cambiarlos reescribiría lo que se pensaba gastar en vez de registrar que se
-- cambió de idea. Para eso está el reemplazo.
--
-- "Dejó de contar el día X" es un hecho: deshacerlo o moverlo de fecha
-- reescribe la historia. updated_at queda fuera a propósito, igual que en
-- transactions, accounts y people: es el marcador de la sincronización.
-- ---------------------------------------------------------------------------
CREATE FUNCTION refuse_budget_rewrite() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.id          IS DISTINCT FROM OLD.id
    OR NEW.category_id IS DISTINCT FROM OLD.category_id
    OR NEW.period      IS DISTINCT FROM OLD.period
    OR NEW.limit_cents IS DISTINCT FROM OLD.limit_cents
    OR NEW.replaces_id IS DISTINCT FROM OLD.replaces_id
    OR NEW.created_at  IS DISTINCT FROM OLD.created_at
    THEN
        RAISE EXCEPTION
            'El presupuesto % no se edita. Registra otro que lo reemplace.',
            OLD.id;
    END IF;

    IF OLD.superseded_at IS NOT NULL
       AND NEW.superseded_at IS DISTINCT FROM OLD.superseded_at
    THEN
        RAISE EXCEPTION
            'El presupuesto % dejó de contar el %; eso no se deshace ni se reescribe',
            OLD.id, OLD.superseded_at;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER budgets_keep_their_identity
    BEFORE UPDATE ON budgets
    FOR EACH ROW EXECUTE FUNCTION refuse_budget_rewrite();

CREATE FUNCTION refuse_budget_delete() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Los presupuestos no se borran. Reemplázalo: la historia lo necesita.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER budgets_are_never_deleted
    BEFORE DELETE ON budgets
    FOR EACH ROW EXECUTE FUNCTION refuse_budget_delete();


-- ---------------------------------------------------------------------------
-- Sin red de SQL para "la categoría es una cuenta de gasto".
--
-- Cruza tablas, así que un CHECK no sirve y haría falta un trigger que
-- consultara accounts. Es la misma decisión que con "una persona no se archiva
-- con cuentas abiertas" en la V6: la regla cruza filas y no mueve plata, y la
-- clave ajena ya garantiza que la categoría existe. Un presupuesto sobre una
-- cuenta que no es de gasto daría un porcentaje sin sentido, no un libro
-- descuadrado, y el dominio lo rechaza al escribir y también al cargar.
--
-- Tampoco hay red para "la categoría no está archivada": eso se comprueba al
-- escribir y a propósito no al cargar, porque una categoría archivada en junio
-- no puede impedir que cargue un presupuesto escrito en marzo.
-- ---------------------------------------------------------------------------

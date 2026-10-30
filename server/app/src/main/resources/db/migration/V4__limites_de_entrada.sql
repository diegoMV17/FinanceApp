-- Los límites de lo que entra, replicados aquí como red de seguridad.
--
-- Las reglas viven en el dominio Java, igual que el balance: ahí dan mensajes
-- útiles y se prueban en milisegundos. Lo que hay aquí es lo que impide que un
-- script, una migración futura o un psql abierto a las dos de la mañana
-- escriban algo que el dominio nunca habría aceptado. Cada CHECK dice de quién
-- es la regla, para que nadie tenga que adivinar cuál de los dos lados cambiar.
--
-- No se tocan la V1, la V2 ni la V3: una migración ya aplicada no se edita.


-- ---------------------------------------------------------------------------
-- Un apunte no mueve más de diez mil millones de pesos.
--
-- Dueña de la regla: Entry.MAX_ABSOLUTE_CENTS (dominio).
--
-- Esto no es estética. Money suma con Math.addExact, así que un único apunte
-- cerca de Long.MAX_VALUE haría que toda suma futura lance ArithmeticException,
-- y como aquí nada se borra, ese movimiento dejaría el libro inservible para
-- siempre.
--
-- Escrito con BETWEEN y no con abs(): abs(-9223372036854775808::bigint) no
-- cabe en bigint y reventaría dentro del propio CHECK, con un mensaje que no
-- dice nada de la regla que se violó.
-- ---------------------------------------------------------------------------
ALTER TABLE entries ADD CONSTRAINT entries_amount_within_range
    CHECK (amount_cents BETWEEN -1000000000000 AND 1000000000000);


-- ---------------------------------------------------------------------------
-- Los tres textos que vienen de afuera y acaban en una pantalla, en un log y
-- en un mensaje de chat.
--
-- Dueños de la regla: Transaction.MAX_DESCRIPTION_LENGTH,
-- Cancellation.MAX_REASON_LENGTH y Account.MAX_NAME_LENGTH (dominio), que
-- cuentan code points después de recortar — que es justo lo que cuenta
-- char_length.
--
-- Y ninguno admite caracteres de control. Un salto de línea dentro de una
-- descripción es una línea de log falsificada: "Almuerzo\nINFO movimiento
-- anulado por el sistema" se lee como dos registros y uno nunca ocurrió.
-- ---------------------------------------------------------------------------
ALTER TABLE transactions ADD CONSTRAINT transactions_description_within_length
    CHECK (char_length(description) <= 200);

ALTER TABLE transactions ADD CONSTRAINT transactions_description_is_one_line
    CHECK (description !~ '[[:cntrl:]]');

ALTER TABLE transactions ADD CONSTRAINT transactions_cancellation_note_within_length
    CHECK (char_length(cancellation_note) <= 200);

ALTER TABLE transactions ADD CONSTRAINT transactions_cancellation_note_is_one_line
    CHECK (cancellation_note !~ '[[:cntrl:]]');

ALTER TABLE accounts ADD CONSTRAINT accounts_name_within_length
    CHECK (char_length(name) <= 60);

ALTER TABLE accounts ADD CONSTRAINT accounts_name_is_one_line
    CHECK (name !~ '[[:cntrl:]]');

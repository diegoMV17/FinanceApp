-- Quién anuló: la app, el bot, un recurrente o el sistema.
--
-- El dominio guarda el origen de cada anulación (Cancellation.source), y el
-- esquema de la V1 solo tenía cuándo y por qué. Sin esta columna, guardar y
-- volver a cargar una anulación del bot la convertiría en una de la app, y
-- justamente ese dato es el que hace falta cuando el bot interpreta mal un
-- mensaje.
--
-- No se toca la V1: una migración ya aplicada no se edita. La tabla no tiene
-- datos reales todavía, así que no hace falta rellenar nada.
ALTER TABLE transactions ADD COLUMN cancellation_source transaction_source;

-- Cuándo y quién van juntos o no van: una anulación sin origen, o un origen sin
-- anulación, son dos verdades que no concuerdan.
ALTER TABLE transactions ADD CONSTRAINT transactions_cancellation_is_complete
    CHECK ((cancelled_at IS NULL) = (cancellation_source IS NULL));

-- El siglo que cubre este libro, replicado aquí como red de seguridad.
--
-- No se tocan la V1, la V2, la V3 ni la V4: una migración ya aplicada no se
-- edita.


-- ---------------------------------------------------------------------------
-- Un movimiento ocurrió entre 2000 y 2099.
--
-- Dueña de la regla: Transaction.EARLIEST_DATE y Transaction.LATEST_DATE
-- (dominio). Si cambia una, cambia la otra.
--
-- Esto no es estética. En Java, LocalDate admite tranquilamente el año
-- 999999999, y DATE en Postgres llega hasta el 5874897 d.C.; una sola fila así
-- se queda al final de todas las consultas "a fecha" para el resto de la vida
-- del libro, y como aquí nada se borra, solo se podría anular, nunca quitar.
-- Una fecha fuera de este rango es un dedazo o un dispositivo con el reloj
-- roto, y el momento más barato de decirlo es antes de guardarla.
--
-- Escrito con literales DATE y no con make_date() ni extract(year ...): el
-- CHECK tiene que ser inmutable, y así además se lee igual que la constante
-- del dominio que copia.
-- ---------------------------------------------------------------------------
ALTER TABLE transactions ADD CONSTRAINT transactions_date_within_range
    CHECK (occurred_on BETWEEN DATE '2000-01-01' AND DATE '2099-12-31');

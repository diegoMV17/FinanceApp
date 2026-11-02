#!/usr/bin/env bash
# Los roles de mínimo privilegio. Lo corre el entrypoint de la imagen de
# Postgres una sola vez, al crear el volumen, desde docker-entrypoint-initdb.d.
#
#   finance_owner  dueño de la base y del esquema; con él corre Flyway.
#   finance_app    con el que trabaja la API: SELECT, INSERT y UPDATE, y
#                  nada más. Nunca DELETE, TRUNCATE ni CREATE.
#
# Es un .sh y no un .sql porque un .sql no puede leer el entorno, y las
# contraseñas salen de ahí (deploy/.env), nunca de una migración ni de este
# archivo. psql las lee con \getenv, así que tampoco aparecen en la línea de
# comandos de ningún proceso.
#
# El mismo archivo lo usa LeastPrivilegeRolesTest en server/: si cambia aquí,
# el test lo prueba tal cual.
set -Eeuo pipefail

: "${FINANCE_OWNER_PASSWORD:?falta FINANCE_OWNER_PASSWORD}"
: "${FINANCE_APP_PASSWORD:?falta FINANCE_APP_PASSWORD}"

psql --no-psqlrc -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv owner_password FINANCE_OWNER_PASSWORD
\getenv app_password FINANCE_APP_PASSWORD
\getenv db POSTGRES_DB

-- Ninguno de los dos administra nada: ni es superusuario, ni crea roles ni
-- bases, ni se salta la seguridad por filas.
CREATE ROLE finance_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOREPLICATION NOBYPASSRLS PASSWORD :'owner_password';
CREATE ROLE finance_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';

-- La base es del dueño. A PUBLIC se le quita todo: por omisión cualquier rol
-- puede conectarse y crear tablas temporales, y una tabla temporal también es
-- un CREATE.
ALTER DATABASE :"db" OWNER TO finance_owner;
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO finance_app;

-- El esquema también. La app lo usa, no crea nada en él.
ALTER SCHEMA public OWNER TO finance_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO finance_app;

-- Lo que el dueño cree de aquí en adelante —las tablas de V1 a V7 y las de
-- cada migración futura— nace con estos permisos para la app. Las secuencias
-- necesitan USAGE para nextval: audit_log.id es un BIGSERIAL.
--
-- Esto también alcanza a flyway_schema_history, que la crea el dueño. Se lo
-- quita el callback afterMigrate de server/, porque cuando este script corre
-- esa tabla todavía no existe.
ALTER DEFAULT PRIVILEGES FOR ROLE finance_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE ON TABLES TO finance_app;
ALTER DEFAULT PRIVILEGES FOR ROLE finance_owner IN SCHEMA public
    GRANT USAGE ON SEQUENCES TO finance_app;
SQL

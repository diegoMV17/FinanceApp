#!/usr/bin/env bash
# Crea deploy/.env con un secreto al azar para cada clave de .env.example.
#
# Se corre una sola vez, antes del primer `docker compose up`. Las tres
# contraseñas de Postgres quedan grabadas en el volumen al crearlo: perder este
# .env deja una base con contraseñas que nadie sabe. Por eso el script se niega
# a sobrescribir uno que ya exista, y por eso hay que guardarlo (cifrado) fuera
# de la VM.
#
# No imprime ningún secreto: lo que sale por la terminal acaba en el historial
# de la terminal, en un log o en una captura de pantalla.
set -Eeuo pipefail

cd "$(dirname "$0")"

# Todo lo que se cree desde aquí nace legible solo para quien lo creó: el .env
# no pasa ni un instante con otros permisos antes del chmod.
umask 077

if [[ -e .env || -L .env ]]; then
    echo "deploy/.env ya existe y no se sobrescribe." >&2
    echo "Si de verdad hay que rehacerlo, las contraseñas del volumen de Postgres" >&2
    echo "también hay que cambiarlas: borrar el .env no las cambia." >&2
    exit 1
fi

# Todos los valores se generan antes de escribir nada: si openssl falla a
# mitad de camino, set -e corta aquí y no queda un .env a medias.
#
# 32 bytes en hexadecimal: 64 caracteres de [0-9a-f], que no hay que escapar ni
# en el .env, ni en una URL de JDBC, ni en el shell.
content=""
while IFS= read -r key; do
    [[ "$key" =~ ^[A-Z_][A-Z0-9_]*$ ]] || { echo "clave inválida en .env.example" >&2; exit 1; }
    value=$(openssl rand -hex 32)
    [[ "$value" =~ ^[0-9a-f]{64}$ ]] || { echo "openssl no devolvió 32 bytes" >&2; exit 1; }
    content+="$key=$value"$'\n'
done < <(grep -v '^#' .env.example | grep -v '^$' | cut -d= -f1)

# noclobber: si algo creó el .env entre la comprobación de arriba y esta
# línea, la escritura falla en vez de pisarlo.
set -o noclobber
printf '%s' "$content" > .env
chmod 600 .env

echo "deploy/.env creado, con permisos 600."
echo "Guarda una copia cifrada fuera de esta máquina: sin ella, la base no se abre."

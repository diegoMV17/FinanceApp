#!/usr/bin/env bash
# Lo que la imagen de la API promete, comprobado contra Docker de verdad.
#
#   1. El contexto de build no lleva secretos ni basura: .env, CLAUDE.md,
#      build/ y .gradle/ no llegan al daemon, así que no pueden acabar en una
#      capa. Se prueba con señuelos y un Dockerfile mínimo (FROM scratch +
#      COPY .) exportado a una carpeta: lo que se mide es el .dockerignore, no
#      lo que el Dockerfile de verdad copia hoy por casualidad.
#   2. La imagen corre como un usuario sin privilegios, con el jar de root y
#      de solo lectura, y la JVM arranca.
#
# Uso: deploy/test/image.test.sh   (desde cualquier carpeta; necesita Docker)
set -Eeuo pipefail

# En Git Bash de Windows, MSYS reescribe los argumentos que parecen rutas
# (/app/... pasa a C:/Program Files/Git/app/...). Aquí van todos al contenedor.
export MSYS_NO_PATHCONV=1

here=$(cd "$(dirname "$0")" && pwd)
server=$(cd "$here/../../server" && pwd)
image=finance-api:test

# Y por lo mismo las rutas del host, las únicas que sí son de Windows, se
# convierten a mano.
host_path() {
    if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi
}

fail() { echo "FALLA: $*" >&2; exit 1; }
pass() { echo "ok - $*"; }

# --- 1. El contexto ----------------------------------------------------------

# Señuelos en la raíz y en un módulo: el patrón tiene que alcanzar las dos
# alturas. Solo se crean los que no existen y solo esos se borran después:
# un CLAUDE.md o un build/ de verdad no se tocan.
canaries=(.env app/.env CLAUDE.md app/CLAUDE.md
          build/canary app/build/canary .gradle/canary app/.gradle/canary)
created=()
workdir=$(mktemp -d)

cleanup() {
    local path
    for path in "${created[@]}"; do
        rm -f "$server/$path"
        rmdir "$(dirname "$server/$path")" 2>/dev/null || true
    done
    rm -rf "$workdir"
}
trap cleanup EXIT

for path in "${canaries[@]}"; do
    if [[ ! -e "$server/$path" ]]; then
        mkdir -p "$(dirname "$server/$path")"
        echo "señuelo: si esto aparece en una imagen, el .dockerignore falló" > "$server/$path"
        created+=("$path")
    fi
done

docker build --quiet -f - --output "type=local,dest=$(host_path "$workdir/context")"     "$(host_path "$server")" > /dev/null <<'DOCKERFILE'
FROM scratch
COPY . /context
DOCKERFILE

[[ -f "$workdir/context/context/settings.gradle.kts" ]] \
    || fail "la exportación del contexto no trae el proyecto; el test no está midiendo nada"

leaked=$(cd "$workdir/context/context" \
    && find . \( -name .env -o -name CLAUDE.md -o -name build -o -name .gradle \) -print)
[[ -z "$leaked" ]] || fail "el contexto de build lleva lo que .dockerignore debía sacar:
$leaked"
pass "el contexto no lleva .env, CLAUDE.md, build/ ni .gradle/"

# --- 2. La imagen ------------------------------------------------------------

docker build --quiet -t "$image" "$(host_path "$server")" > /dev/null
pass "la imagen se construye"

uid=$(docker run --rm --entrypoint id "$image" -u)
[[ "$uid" != "0" ]] || fail "la imagen corre como root"
pass "corre como el UID $uid, no como root"

docker run --rm --entrypoint sh "$image" -c \
    'test -r /app/finance-api.jar && test ! -w /app/finance-api.jar' \
    || fail "el proceso puede reescribir su propio jar"
pass "el jar se lee y no se escribe"

docker run --rm --entrypoint sh "$image" -c 'test ! -e /src && ! command -v gradle' \
    || fail "la imagen final trae el código o Gradle de la etapa de build"
pass "la imagen final no trae el código ni Gradle"

docker run --rm --entrypoint java "$image" -version > /dev/null 2>&1 \
    || fail "la JVM de la imagen no arranca"
pass "la JVM arranca"

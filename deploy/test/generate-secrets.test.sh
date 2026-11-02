#!/usr/bin/env bash
# Lo que deploy/generate-secrets.sh promete. Corre sobre una copia en una
# carpeta temporal: nunca toca el deploy/.env de verdad.
#
# Uso: deploy/test/generate-secrets.test.sh   (necesita openssl)
set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

fail() { echo "FALLA: $*" >&2; exit 1; }
pass() { echo "ok - $*"; }

fresh_copy() {
    rm -rf "$workdir/deploy" && mkdir "$workdir/deploy"
    cp "$here/../generate-secrets.sh" "$here/../.env.example" "$workdir/deploy/"
}

# --- crea el .env ------------------------------------------------------------
fresh_copy
output=$(bash "$workdir/deploy/generate-secrets.sh" 2>&1) || fail "no terminó bien: $output"
env_file=$workdir/deploy/.env
[[ -f "$env_file" ]] || fail "no creó el .env"
pass "crea el .env"

# En Windows (Git Bash) los permisos de NTFS no se leen como en Linux: allí
# esta comprobación no dice nada y se avisa en vez de mentir. En CI y en la VM
# sí se comprueba.
mode=$(stat -c '%a' "$env_file")
if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
    echo "aviso - permisos no comprobables en $(uname -s); en Linux tienen que ser 600 (aquí: $mode)"
else
    [[ "$mode" == "600" ]] || fail "el .env queda con permisos $mode y no 600"
    pass "el .env queda con permisos 600"
fi

expected_keys=$(grep -v '^#' "$workdir/deploy/.env.example" | grep -v '^$' | cut -d= -f1 | sort)
actual_keys=$(cut -d= -f1 "$env_file" | sort)
[[ "$actual_keys" == "$expected_keys" ]] || fail "las claves no son las de .env.example:
$actual_keys"
pass "trae exactamente las claves de .env.example"

values=$(cut -d= -f2- "$env_file")
while read -r value; do
    # 64 caracteres hexadecimales son 32 bytes de azar. Solo [0-9a-f]: nada
    # que el .env, una URL de JDBC o el shell interpreten.
    [[ "$value" =~ ^[0-9a-f]{64,}$ ]] || fail "un valor no es hexadecimal de al menos 32 bytes"
    if [[ "$output" == *"$value"* ]]; then
        fail "imprimió un secreto"
    fi
done <<< "$values"
[[ $(sort -u <<< "$values" | wc -l) -eq $(wc -l <<< "$values") ]] || fail "repite un secreto"
pass "cada secreto es distinto, de al menos 32 bytes, y ninguno se imprime"

# --- no sobrescribe ----------------------------------------------------------
before=$(cat "$env_file")
if bash "$workdir/deploy/generate-secrets.sh" > /dev/null 2>&1; then
    fail "con un .env existente terminó bien"
fi
[[ "$(cat "$env_file")" == "$before" ]] || fail "sobrescribió el .env existente"
pass "se niega a sobrescribir un .env existente y lo deja intacto"

# --- si openssl falla, no deja nada a medias ---------------------------------
fresh_copy
mkdir "$workdir/fakebin"
printf '#!/bin/sh\nexit 1\n' > "$workdir/fakebin/openssl"
chmod +x "$workdir/fakebin/openssl"
if PATH="$workdir/fakebin:$PATH" bash "$workdir/deploy/generate-secrets.sh" > /dev/null 2>&1; then
    fail "con openssl roto terminó bien"
fi
[[ ! -e "$workdir/deploy/.env" ]] || fail "con openssl roto dejó un .env a medias"
pass "si openssl falla no deja un .env a medias"

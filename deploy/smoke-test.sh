#!/usr/bin/env bash
# Prueba de humo contra una API ya levantada: la del compose local, la de la
# VM o la de Oracle.
#
#   1. Sin token: 401.  Con token: 200.
#   2. Quince tokens malos, y el bueno sigue entrando. Detrás del NAT de Docker
#      y de Tailscale Serve todas las peticiones llegan con la misma dirección:
#      si el bloqueo frenara también al token bueno, cualquiera dejaría fuera
#      al dueño.
#   3. Un recorrido corto con datos inventados: dos cuentas (un gasto necesita
#      de dónde sale y en qué categoría cae), un gasto y el resumen.
#
# La URL y el token se piden por la terminal; el token con `read -s`. Nunca
# como argumento: quedaría en el historial del shell y, mientras corre, en la
# lista de procesos. Por lo mismo a curl le llega en un archivo de
# configuración por la entrada estándar y no en su línea de comandos.
#
# Uso: deploy/smoke-test.sh   (necesita curl y openssl)
set -Eeuo pipefail

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

fail() { echo "FALLA: $*" >&2; exit 1; }
pass() { echo "ok - $*"; }

cat >&2 <<'AVISO'
AVISO: esta prueba escribe en el libro dos cuentas y un gasto inventados
("Prueba de humo" y la fecha). En este libro nada se borra: se quedan para
siempre. El gasto se puede anular y las cuentas archivar, pero las filas
siguen ahí. Córrela contra un libro de verdad solo si eso te parece bien.
AVISO

read -r -p "URL de la API (por ejemplo http://127.0.0.1:8080): " base_url
base_url=${base_url%/}
[[ "$base_url" =~ ^https?://[^[:space:]]+$ ]] || fail "eso no es una URL http(s)"

read -r -s -p "Token: " token
echo >&2
[[ -n "$token" ]] || fail "sin token no hay prueba"

read -r -p "¿Seguir y escribir los datos de prueba? Escribe si: " answer
[[ "$answer" == "si" || "$answer" == "sí" ]] || { echo "No se escribió nada." >&2; exit 1; }

# Un pedido a la API. Imprime el código de estado; el cuerpo queda en
# $workdir/body. El token viaja dentro de la configuración que curl lee de su
# entrada estándar, con las comillas y barras escapadas como curl las espera.
request() {
    local method=$1 path=$2 bearer=$3 body=${4:-}
    local args=(--silent --show-error --max-time 15 --config -
                --output "$workdir/body" --write-out '%{http_code}'
                --request "$method")
    if [[ -n "$body" ]]; then
        args+=(--header "Content-Type: application/json" --data-binary "$body")
    fi
    {
        if [[ -n "$bearer" ]]; then
            local escaped=${bearer//\\/\\\\}
            escaped=${escaped//\"/\\\"}
            printf 'header = "Authorization: Bearer %s"\n' "$escaped"
        fi
    } | curl "${args[@]}" "$base_url$path"
}

# Lo único que se muestra de un error es su tipo, que es uno de la lista
# cerrada de la API: nunca el cuerpo entero.
expect() {
    local expected=$1 actual=$2 what=$3
    if [[ "$actual" != "$expected" ]]; then
        local type
        type=$(grep -o '"type":"[^"]*"' "$workdir/body" 2>/dev/null || true)
        fail "$what: esperaba $expected y llegó $actual ${type}"
    fi
}

# Un UUID v4 hecho con openssl, para no depender de uuidgen.
uuid() {
    local hex
    hex=$(openssl rand -hex 16)
    printf '%s-%s-4%s-%x%s-%s\n' "${hex:0:8}" "${hex:8:4}" "${hex:13:3}" \
        $(( (16#${hex:16:1} & 3) | 8 )) "${hex:17:3}" "${hex:20:12}"
}

# --- 1. La puerta ------------------------------------------------------------

# 401, o 429 si la dirección sigue bloqueada por los tokens malos de una
# corrida anterior de esta misma prueba (el bloqueo dura un minuto). Las dos
# dicen lo que importa aquí: sin token no se entra.
status=$(request GET /api/balances "")
[[ "$status" == 401 || "$status" == 429 ]] || fail "sin token: esperaba 401 o 429 y llegó $status"
pass "sin token: $status"

expect 200 "$(request GET /api/balances "$token")" "con token"
pass "con token: 200"

# --- 2. El bloqueo no deja fuera al dueño -------------------------------------

blocked=0
for attempt in $(seq 1 15); do
    status=$(request GET /api/balances "token-equivocado-$attempt")
    case "$status" in
        401) ;;
        429) blocked=$((blocked + 1)) ;;
        *) fail "un token malo recibió $status" ;;
    esac
done
# Si ninguno llegó a 429, el limitador no está puesto o su tope es mayor que
# quince, y la comprobación siguiente no estaría probando nada.
(( blocked > 0 )) || fail "quince tokens malos y ningún 429: el limitador no está actuando"
pass "quince tokens malos: los últimos $blocked reciben 429"

expect 200 "$(request GET /api/balances "$token")" "el token bueno tras el bloqueo"
pass "y el token bueno sigue entrando"

# --- 3. Un recorrido corto ----------------------------------------------------

stamp=$(date '+%Y-%m-%d %H:%M:%S')
today=$(date '+%Y-%m-%d')
wallet=$(uuid)
category=$(uuid)
expense=$(uuid)
amount=1234

expect 201 "$(request POST /api/accounts "$token" \
    "{\"id\":\"$wallet\",\"type\":\"ASSET\",\"name\":\"Prueba de humo $stamp\"}")" \
    "abrir la cuenta"
expect 201 "$(request POST /api/accounts "$token" \
    "{\"id\":\"$category\",\"type\":\"EXPENSE\",\"name\":\"Prueba de humo $stamp\"}")" \
    "abrir la categoría"
pass "abre una cuenta y una categoría"

expect 201 "$(request POST /api/movements "$token" \
    "{\"type\":\"EXPENSE\",\"id\":\"$expense\",\"date\":\"$today\",\"description\":\"Prueba de humo\",\"paidFrom\":\"$wallet\",\"category\":\"$category\",\"amountInCents\":$amount}")" \
    "registrar el gasto"
pass "registra un gasto"

expect 200 "$(request GET "/api/summary?asOf=$today" "$token")" "el resumen"
spent=$(grep -o '"expensesThatMonthInCents":-\{0,1\}[0-9]*' "$workdir/body" | cut -d: -f2)
if [[ -z "$spent" ]] || (( spent < amount )); then
    fail "el resumen del mes no cuenta el gasto que se acaba de registrar"
fi
pass "el resumen del mes lo cuenta"

echo "Prueba de humo superada."

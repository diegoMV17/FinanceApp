#!/usr/bin/env bash
# Lo que deploy/compose.yaml promete, leído de `docker compose config`, que es
# el archivo ya resuelto tal como lo va a usar Docker, y no del YAML a mano.
#
# Lo que más importa está arriba: Postgres no publica ningún puerto y vive en
# una red sin salida, y la API publica solo en 127.0.0.1. Docker publica los
# puertos por delante de ufw, así que un `ports:` de más en db dejaría la base
# visible en toda la red aunque el firewall diga «deny», y sin un solo aviso.
#
# Uso: deploy/test/compose.test.sh   (necesita Docker Compose y Python 3; el
# intérprete se elige con PYTHON, por omisión python3)
set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
compose_file=$here/../compose.yaml
python=${PYTHON:-python3}
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

fail() { echo "FALLA: $*" >&2; exit 1; }
pass() { echo "ok - $*"; }

# Valores inventados y distintos entre sí, para poder comprobar que cada uno
# llega del .env y no de un literal escrito en el compose.
keys=(POSTGRES_PASSWORD FINANCE_OWNER_PASSWORD FINANCE_APP_PASSWORD API_TOKEN)
for key in "${keys[@]}"; do
    echo "$key=valor-de-prueba-$key"
done > "$workdir/test.env"

# --env-file explícito: así el test nunca lee el deploy/.env de verdad.
docker compose --env-file "$workdir/test.env" -f "$compose_file" config --format json \
    > "$workdir/config.json"

PYTHONIOENCODING=utf-8 "$python" - "$workdir/config.json" <<'PY'
import json
import sys

config = json.load(open(sys.argv[1], encoding="utf-8"))
failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


services = config["services"]
networks = config["networks"]
check(sorted(services) == ["api", "db"], f"servicios inesperados: {sorted(services)}")
db, api = services["db"], services["api"]

# --- db ---------------------------------------------------------------------
check(not db.get("ports"), "db publica puertos: la base quedaría visible en la red")
check(not db.get("expose"), "db declara expose")
check(list(db.get("networks", {})) == ["internal"], "db no está solo en la red interna")
check(networks["internal"].get("internal") is True, "la red interna tiene salida")
check(db["image"].startswith("postgres:16-alpine@sha256:"),
      "db sin postgres:16-alpine fijado por digest")

volumes = {v["target"]: v for v in db.get("volumes", [])}
data = volumes.get("/var/lib/postgresql/data", {})
check(data.get("type") == "volume", "los datos de db no van en un volumen con nombre")
roles = volumes.get("/docker-entrypoint-initdb.d/10-roles.sh", {})
check(roles.get("source", "").replace("\\", "/").endswith("deploy/postgres/10-roles.sh"),
      "db no monta el script de roles en initdb.d")
check(roles.get("read_only") is True, "el script de roles se monta con escritura")

health = " ".join(db.get("healthcheck", {}).get("test", []))
check("pg_isready" in health, "db sin healthcheck con pg_isready")
# Durante initdb el servidor temporal escucha solo por socket: preguntar por
# TCP es lo que impide que la base salga sana antes de tener los roles.
check("-h 127.0.0.1" in health, "el healthcheck pregunta por el socket y no por TCP")
check("no-new-privileges:true" in db.get("security_opt", []), "db sin no-new-privileges")

# --- api --------------------------------------------------------------------
ports = api.get("ports", [])
check(len(ports) == 1, f"la api publica {len(ports)} puertos")
for port in ports:
    check(port.get("host_ip") == "127.0.0.1",
          f"la api publica en {port.get('host_ip')!r}, no en loopback")
    check(str(port.get("published")) == "8080" and port.get("target") == 8080,
          "la api no publica 8080:8080")
check(sorted(api.get("networks", {})) == ["edge", "internal"],
      "la api no está en internal y edge")
check(not networks["edge"].get("internal"), "la red de borde es interna y no podría publicar")
check(api.get("depends_on", {}).get("db", {}).get("condition") == "service_healthy",
      "la api no espera a que db esté sana")
check(api.get("read_only") is True, "la api no tiene el sistema de archivos de solo lectura")
check(any(t.split(":")[0] == "/tmp" for t in api.get("tmpfs", [])), "la api sin tmpfs en /tmp")
check(api.get("cap_drop") == ["ALL"], "la api no suelta todas las capacidades")
check(not api.get("cap_add"), "la api recupera capacidades con cap_add")
check("no-new-privileges:true" in api.get("security_opt", []), "la api sin no-new-privileges")
check(api.get("restart") == "unless-stopped", "la api sin restart: unless-stopped")
check(not api.get("privileged"), "la api es privilegiada")
memory = api.get("deploy", {}).get("resources", {}).get("limits", {}).get("memory")
check(memory, "la api sin límite de memoria")

env = api.get("environment", {})
check(env.get("SERVER_ADDRESS") == "0.0.0.0",
      "la api no escucha en todas las interfaces del contenedor")
check(env.get("DB_USER") == "finance_app", "la api no trabaja como finance_app")
check(env.get("SPRING_FLYWAY_USER") == "finance_owner", "Flyway no migra como finance_owner")

for name, service in services.items():
    logging = service.get("logging", {})
    options = logging.get("options", {})
    check(logging.get("driver") == "json-file" and "max-size" in options and "max-file" in options,
          f"{name} sin rotación de logs")

# --- secretos ---------------------------------------------------------------
expected = {
    ("db", "POSTGRES_PASSWORD"): "POSTGRES_PASSWORD",
    ("db", "FINANCE_OWNER_PASSWORD"): "FINANCE_OWNER_PASSWORD",
    ("db", "FINANCE_APP_PASSWORD"): "FINANCE_APP_PASSWORD",
    ("api", "DB_PASSWORD"): "FINANCE_APP_PASSWORD",
    ("api", "SPRING_FLYWAY_PASSWORD"): "FINANCE_OWNER_PASSWORD",
    ("api", "API_TOKEN"): "API_TOKEN",
}
for (service, variable), key in expected.items():
    value = services[service].get("environment", {}).get(variable)
    check(value == f"valor-de-prueba-{key}", f"{service}.{variable} no sale de {key} en el .env")

if failures:
    print("\n".join(f"FALLA: {f}" for f in failures), file=sys.stderr)
    sys.exit(1)
PY
pass "db sin puertos y en la red interna; la api solo en 127.0.0.1:8080 y endurecida"
pass "cada secreto sale del .env"

# Sin una clave, el compose se niega a arrancar en vez de levantar algo con
# una contraseña vacía.
for key in "${keys[@]}"; do
    grep -v "^$key=" "$workdir/test.env" > "$workdir/missing.env"
    if docker compose --env-file "$workdir/missing.env" -f "$compose_file" config \
        > /dev/null 2>&1; then
        fail "sin $key el compose arranca igual"
    fi
done
pass "sin cualquiera de las claves, el compose no arranca"

# .env.example dice exactamente qué claves hay que poner, y ninguna con valor.
example=$here/../.env.example
diff <(grep -v '^#' "$example" | grep -v '^$' | sort) \
     <(printf '%s=\n' "${keys[@]}" | sort) > /dev/null \
    || fail ".env.example no lista exactamente las claves del compose, vacías"
pass ".env.example lista las claves del compose, sin valores"

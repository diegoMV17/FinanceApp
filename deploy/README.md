# deploy/

Postgres y la API en una máquina con Docker Compose: la VM local primero,
Oracle después. Todo se corre desde esta carpeta.

Lo que hace falta: Docker con Compose v2, `openssl` y `curl`. La imagen de la
API se construye aquí mismo desde `../server`.

## Levantarlo la primera vez

```sh
./generate-secrets.sh          # crea .env con contraseñas y token al azar
docker compose up -d --build
./smoke-test.sh                # pide la URL (http://127.0.0.1:8080) y el token
```

- `.env` no se versiona y `generate-secrets.sh` se niega a sobrescribirlo. Las
  contraseñas de Postgres quedan grabadas en el volumen al crearlo: **guarda
  una copia cifrada de `.env` fuera de la máquina**. Sin ella, la base no se
  abre.
- El token para el teléfono y para `smoke-test.sh` es `API_TOKEN`, en `.env`.
  Se copia de ahí a mano; ningún script lo imprime.
- `smoke-test.sh` deja en el libro dos cuentas y un gasto de prueba **para
  siempre**: aquí nada se borra.

## Qué queda escuchando

- La API, solo en `127.0.0.1:8080`. Desde afuera se llega por Tailscale Serve,
  que es quien pone el cifrado.
- Postgres, **en ningún puerto del host**: vive en la red `internal` de Docker y
  solo la API lo ve. Docker publica los puertos por delante de `ufw`, así que
  esto no se arregla con el firewall: se arregla no publicándolo. Para
  comprobarlo, `docker compose port db 5432` no tiene que devolver nada.

## Actualizar

```sh
git pull
docker compose up -d --build
```

Las migraciones nuevas las aplica Flyway al arrancar la API, como
`finance_owner`. Si la API no arranca, `docker compose logs api` dice por qué.

## Apagar

```sh
docker compose down            # apaga y conserva los datos
```

**Nunca `docker compose down -v`** sobre un libro de verdad: `-v` borra el
volumen `db-data`, que es el libro entero.

## Usuarios de la base

`postgres/10-roles.sh` corre una sola vez, cuando el volumen está vacío:

- `postgres`, el superusuario: solo para `initdb` y para una emergencia.
- `finance_owner`, dueño del esquema: con él migra Flyway.
- `finance_app`, con el que trabaja la API: SELECT, INSERT y UPDATE, y nada
  más.

Cambiar `POSTGRES_PASSWORD`, `FINANCE_OWNER_PASSWORD` o `FINANCE_APP_PASSWORD`
en `.env` **no** cambia la contraseña del volumen: hay que hacerlo también con
`ALTER ROLE` dentro de la base.

## Tests

`test/` comprueba el compose, el generador de secretos y la imagen. Los corre
el trabajo `image` de CI, que además levanta este compose y le pasa
`smoke-test.sh`.

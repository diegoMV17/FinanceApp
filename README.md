# FinanceApp

Finanzas personales con contabilidad de partida doble: ingresos y gastos,
préstamos y deudas por persona, saldos, presupuestos y estadísticas. Los saldos
nunca se guardan, se derivan de los asientos, y nada se borra: un movimiento
equivocado se anula o se corrige, y queda el rastro.

Es un monorepo con dos proyectos que no se compilan entre sí.

```
FinanceApp/
├── server/     la API y todas las reglas del libro (Java 21, Gradle, Spring Boot, Postgres)
├── mobile/     la app para Android e iOS (Flutter), en construcción
├── contract/   openapi.json, el contrato entre los dos (llega con la app)
└── .github/    CI y Dependabot
```

- **`server/` es la única verdad.** El libro, la contabilidad y todos los
  saldos viven ahí: `domain/` (el dominio, en Java puro) y `app/` (los casos de
  uso y la infraestructura: JPA, Flyway y la API REST).
- **`mobile/` es un cliente.** Muestra, valida el formato de lo que se escribe
  y guarda una cola de movimientos pendientes para funcionar sin conexión; no
  decide nada contable.
- En la raíz no hay build. **Cada comando se corre dentro de su carpeta.**

## El servidor

Desde `server/`:

```bash
./gradlew test            # todo
./gradlew :domain:test    # solo el dominio (no necesita Docker)
```

Hace falta un JDK 21 y **Docker corriendo**: los tests de infraestructura
levantan un Postgres real con Testcontainers. Sin Docker esos tests se saltan y
el build dice BUILD SUCCESSFUL con todo SKIPPED, que es un verde que no vale.

La aplicación lee `DB_URL`, `DB_USER`, `DB_PASSWORD` y `API_TOKEN` del entorno.
**Son obligatorias y no tienen valor por omisión** a propósito: un valor por
defecto en el repo es un secreto publicado. `server/example.env` es la
plantilla; el archivo con los valores de verdad nunca entra al repositorio.

## La app

Hoy es el esqueleto: arranque y tema, el analizador estricto, el test que
impone la arquitectura de los imports y `Money` en centavos. Todavía no hay
pantallas ni red. Desde `mobile/`:

```bash
flutter pub get
flutter analyze --fatal-infos   # en CI un aviso es un error
flutter test
```

Hace falta Flutter estable 3.47.9, la versión que fija CI. El proyecto nace
con Android e iOS; compilar para iOS exige un Mac, pero el analizador y los
tests no dependen de ninguna plataforma.

## CI

Un workflow con un trabajo por proyecto, los dos corriendo siempre y sin
filtros de ruta: `test` (Gradle, dentro de `server/`) y `mobile`
(`flutter analyze --fatal-infos` y `flutter test`, dentro de `mobile/`). Con
filtros, un check obligatorio que no corre no sale rojo: deja el PR esperando.

Dependabot vigila tres ecosistemas, cada uno en su carpeta: `gradle` en
`/server`, `pub` en `/mobile` y `github-actions` en `/`.

## Licencia

MIT. Ver [LICENSE](LICENSE).

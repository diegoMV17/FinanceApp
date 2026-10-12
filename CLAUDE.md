# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

FinanceApp: gestión de finanzas personales (un solo usuario) basada en contabilidad de partida doble. Java 21, Gradle multi-módulo, Spring Boot 4.1.1, JPA, Flyway y Postgres. Los comentarios de infraestructura y SQL están en español; el dominio está documentado en inglés.

## Rol al desarrollar aquí

Cada vez que se pida desarrollar algo en este repo, actuar como un ingeniero de software especializado en ciberseguridad, no solo como quien hace que la funcionalidad compile — además de seguir la arquitectura y el clean code de abajo. Este proyecto guarda datos financieros reales y sensibles de un solo usuario, y va a quedar expuesto por un bot de Telegram y un servidor en una nube pública: nada se expone fácilmente a propósito, y tampoco debe exponerse fácilmente por descuido.

## Comandos

- Todo: `./gradlew test` (es lo que corre CI en push a `main` y en PRs, con Java 21)
- Solo dominio (rápido, sin Docker): `./gradlew :domain:test`
- Solo infraestructura: `./gradlew :app:test`
- Un test: `./gradlew :domain:test --tests "com.personalfinance.domain.ledger.LedgerTest"` (o `...LedgerTest.metodo`)
- Los tests de `app` usan Testcontainers con `postgres:16-alpine`. Se saltan (no fallan) si no hay Docker.
- La app en ejecución lee `DB_URL`, `DB_USER` y `DB_PASSWORD`, **obligatorias y sin valor por defecto**: si falta una, la app no arranca. No agregues defaults a `application.yml`; una clave por defecto queda publicada en el repo.
- Sin Docker Desktop abierto, `:app:test` sale SKIPPED y el build dice BUILD SUCCESSFUL: es un verde falso. Usa `--rerun-tasks` si Gradle cacheó una corrida sin Docker.
- No hay linter ni formateador configurado.

## Arquitectura

- Dos módulos: `domain` (Java puro) y `app` (Spring Boot, depende de `domain`). Gradle hace cumplir que el dominio no importe Spring/JPA porque no están en su classpath: no añadas dependencias de framework a `domain/build.gradle.kts` ni anotaciones JPA a las clases del dominio. El `build.gradle.kts` raíz no declara dependencias a propósito.
- Dentro de `app` hay dos capas: `application` (casos de uso, puertos, comandos, lecturas) e `infrastructure` (JPA, Flyway, adaptadores). `application` **no importa** `infrastructure`; `ArchitectureTest` lo impone con ArchUnit, porque comparten módulo y ahí Gradle no puede imponer nada.
- Modelo contable (`domain/.../ledger`): `Ledger` es el libro de `Transaction`s compuestas por `Entry`s. Los saldos nunca se almacenan, siempre se derivan de los apuntes. Nada se borra: `cancel` anula una transacción y `amend` anula la original y registra una corrección que apunta a ella. Las consultas reciben la fecha como parámetro; el dominio no lee el reloj.
- `Operations` son plantillas (apertura, gasto, préstamo, transferencia, fondos de terceros…) que construyen `Transaction`s ordinarias. No hay lógica especial por tipo de operación en otro sitio: solo cambian las cuentas que tocan los apuntes. El llamante pasa montos positivos y `Operations` decide los signos.
- `Chart` es el catálogo de cuentas (`AccountType`, `BalanceNature`). Las cuentas se archivan, nunca se borran, y el nombre es único entre las abiertas del mismo tipo.
- `Money` es un record de centavos enteros (`long`); nunca uses float/double. El signo indica el lado del asiento, no si el monto es "bueno" o "malo".
- Los errores de negocio son excepciones bajo `LedgerException`.
- Esquema: Flyway es el dueño (`app/src/main/resources/db/migration`). Hibernate corre en `ddl-auto: validate`, nunca `update`. El SQL replica reglas del dominio como red de seguridad (triggers plpgsql, enums, índices únicos parciales), por eso los tests de integración usan Postgres real y no H2. Un cambio de invariantes en el dominio debe reflejarse en una migración nueva; no edites migraciones ya aplicadas.
- Persistencia (`app/.../infrastructure/persistence`): las clases `Row` son entidades JPA separadas del dominio, y `LedgerMapper` traduce usando solo las factorías públicas `rehydrated(...)`. Cada `Row` extiende `AssignedIdRow` (implementa `Persistable`): el id lo pone el dominio, y sin eso Spring Data hace `merge` en vez de `insert`, así que un guardado repetido reescribiría la fila y duplicaría los apuntes en silencio. Toda `Row` nueva debe extenderla. Las columnas que no cambian van con `updatable = false`.
- Los stores rechazan lo mismo que el dominio (registrar dos veces, anular dos veces, archivar dos veces) con las mismas excepciones: no asumen que el libro en memoria ya lo revisó.
- Una corrección se guarda con `TransactionStore.amend(...)`, en un solo commit. Nunca `markCancelled` + `record` por separado: si falla la segunda mitad, el movimiento desaparece.
- Desde la V3 la base solo admite, sobre una transacción guardada, anularla una vez; una cuenta no cambia de tipo, no se desarchiva ni se borra; la auditoría solo crece; y `entries.position` es obligatoria y única por transacción.
- Spring Boot 4 reparte las autoconfiguraciones por módulo: hace falta `spring-boot-flyway` además de `flyway-core`, o Flyway no arranca y no deja ningún log. Postgres necesita `flyway-database-postgresql`, y Testcontainers lleva su propio BOM porque el de Spring Boot 4 ya no lo gestiona.

## Casos de uso (`app/.../application`)

La capa entre lo externo (API REST, bot) y el dominio. Todo lo que entra por aquí es entrada no confiable.

- **Forma de toda escritura**, en un solo `@Transactional`: `lock.acquire()` → `loader.load()` → ¿ya aplicado? → regla en el dominio → persistir por el puerto → `audit.record(...)`. Una clase por caso de uso, un único `execute`.
- **El candado va primero, siempre.** `BookLock` toma `pg_advisory_xact_lock` con una clave fija para todo el libro, como primera sentencia de la transacción, y se suelta al terminarla. Moverlo después de `load()` rompe su propósito: la segunda escritura decidiría sobre un libro que no vio lo que acaba de guardar la primera (lo prueban C2 y C3). Hace falta aunque la app sea de un solo usuario: hay reglas que cruzan filas y que la base no ve (archivar exige saldo eventual cero), y el reintento por timeout del cliente es el caso más probable.
- **Nunca `REPEATABLE READ` en una escritura**: la foto se tomaría al empezar a esperar el candado y el libro cargado no vería lo que la escritura anterior acaba de guardar. Las escrituras van en el `READ COMMITTED` por omisión de Postgres, dicho en un comentario donde se declara.
- **Las lecturas sí van en `REPEATABLE READ`** (`readOnly = true`) y no toman el candado. `BookLoader` hace dos consultas (cuentas, luego movimientos); en READ COMMITTED una escritura entre las dos deja un apunte apuntando a una cuenta que no se cargó y la lectura revienta con `UnknownAccountException` (lo prueba C6; C5 no alcanza, la ventana es de microsegundos).
- **`BookLoader` es `MANDATORY`**: solo se carga el libro dentro de la transacción de un caso de uso, que es la que fija el aislamiento y el candado. Para llamarlo desde un test hay que abrir una transacción de solo lectura a mano.
- **Idempotencia.** Los ids los genera el cliente. Id nuevo → `APPLIED`; id repetido con el mismo contenido → `ALREADY_APPLIED` sin escribir nada; id repetido con otro contenido → excepción de conflicto. La detección la hace el caso de uso con `sameContentAs`, antes de llamar al dominio; `Ledger` y los stores siguen rechazando registrar o anular dos veces. La API traducirá `APPLIED`→201, `ALREADY_APPLIED`→200, conflicto→409 y `BookBusyException`→503 con `Retry-After`.
- **`ALREADY_APPLIED` no audita.** Una fila de `audit_log` por cada `APPLIED`, dentro de la misma transacción; ninguna por un reintento ni por un fallo. `before`/`after` solo donde el dato se perdería: `RENAME` guarda el nombre anterior y el nuevo, `AMEND` el id del reemplazo. Nunca montos ni descripciones: ya están en tablas inmutables.
- **No loguear comandos.** Son records y su `toString` imprime descripciones, montos y nombres de cuentas. Si alguna vez hace falta dejar rastro, solo ids.
- **Límites de entrada en el dominio, con su `CHECK` en la V4**: monto por apunte ≤ 1.000.000.000.000 centavos, descripción y motivo de anulación ≤ 200, nombre de cuenta ≤ 60, los tres contando code points después de recortar y sin caracteres de control. Viven en los constructores (así `rehydrated` también los aplica) y la V4 los replica como red de seguridad. Si cambias uno, cambia los dos.
- **El reloj es un bean en UTC truncado a microsegundos**, que es la precisión de un `TIMESTAMPTZ`: con nanosegundos, la fecha del libro en memoria no es la que la base puede guardar y el mismo hecho queda con dos fechas. El dominio sigue sin reloj; la hora la pone el caso de uso.
- **Ningún `switch` por tipo de operación.** Cada `MovementCommand` sabe construir su `Transaction` con `Operations`, igual que el dominio no tiene lógica especial por operación.

## Clean code (requisito permanente, no solo de este proyecto)

- Las invariantes viven pegadas al dato que protegen, en constructores: debe ser imposible construir un objeto inválido, no solo indeseable.
- Una regla, un lugar. Si algo se duplica en SQL y en Java (como las invariantes de balance), es una segunda verdad que alguien tendrá que recordar actualizar a mano.
- Mutadores package-private cuando solo el agregado debe poder llamarlos (`Transaction.cancel`, `Account.archive`).
- Identificadores en inglés; los datos del propio usuario (nombres de cuentas) en español.
- Nada de código sin un test que lo pida. Si no hay test, no se escribe.
- Construir en capas: cuando una pieza es grande, primero la estructura (puertos, firmas, esqueleto) y luego el contenido — no al revés.

## Seguridad (requisito permanente del usuario — ver también el documento de principios de seguridad del proyecto en claude.ai)

- **Secretos:** nunca en código ni comiteados — token del bot de Telegram, credenciales de la base de datos, cualquier clave, siempre por variable de entorno o archivo fuera del repo. Si algo se comitea por error, no basta con borrarlo en el siguiente commit: hay que rotarlo, porque ya quedó en el historial de git.
- **Entradas externas no confiables:** la API REST, el bot de Telegram y el futuro parser de lenguaje natural (HU28) son las tres puertas de entrada. Validar tipo/rango/formato antes de que la entrada toque el dominio (la última línea de defensa es que `Transaction` no se construye desbalanceada, no la única). Nunca SQL por concatenación — mantener parámetros con JPA/Hibernate. El texto del bot nunca se ejecuta directo: siempre pasa por confirmación (HU29) antes de registrarse.
- **Bot de Telegram:** validar `chat_id` contra una whitelist. No es opcional ni "para después" — sin eso, cualquiera que descubra el bot puede inyectar movimientos.
- **API REST:** aunque corra sobre Tailscale (red privada cifrada), exige su propia autenticación — nunca confiar solo en que la red es privada.
- **Servidor (Oracle Cloud):** firewall deny-by-default, solo abrir lo estrictamente necesario; SSH solo con llave, nunca contraseña; nada de herramientas de administración expuestas en producción.
- **Base de datos:** el usuario que usa la app tiene permisos mínimos sobre sus propias tablas, nunca es el superusuario de Postgres.
- **Logs:** nunca imprimir secretos, tokens ni la cadena de conexión completa. La auditoría de dominio (`audit_log`, `cancelled_at`, `replaces`) es distinta de los logs de aplicación y ya cubre el historial de movimientos — no hace falta duplicarla.
- **Dependencias y backups:** atender alertas de seguridad de dependencias (Dependabot). Cualquier backup de la base de datos se cifra antes de guardarse fuera de la VM.

## Tests

- Dominio: JUnit 5 + AssertJ + jqwik. `LedgerPropertiesTest` son tests de propiedades; `LedgerScenario` y `TestAccounts` son helpers.
- Casos de uso y concurrencia: tests `U*` y `C*` en `app/.../application`. Los de concurrencia no pueden ir dentro de una transacción de test y lanzan menos hilos que conexiones tiene el pool de Hikari (diez por omisión), o medirían el pool y no el candado. `BookLockTimeoutTest` y los que usan `@MockitoSpyBean` tienen su propio contexto, y por tanto su propio contenedor.
- Infraestructura: extiende `PostgresIntegrationTest`. El contenedor es un `@Bean` de Spring (`PostgresContainer`, con `@ServiceConnection`), no un campo `@Container static`: JUnit apagaría ese contenedor al terminar cada clase mientras Spring sigue reutilizando el contexto en caché, y la clase siguiente quedaría conectada a un Postgres muerto (cada test espera 30 s y falla). Todas las clases comparten un contenedor; cada test limpia las tablas con `TRUNCATE` en su `@BeforeEach`.
- Los tests del esquema prueban triggers diferidos: van con `@Transactional(propagation = NOT_SUPPORTED)` y escriben transacción + apuntes en un solo commit (`TransactionTemplate`).
- UTF-8 está fijado en `gradle.properties` y en `JavaCompile` porque hay acentos en el código y el SQL.

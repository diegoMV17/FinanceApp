# mobile

La app de FinanceApp para Android e iOS. Es un **cliente**: el libro, las
reglas contables y todos los saldos viven en `../server/`. La app muestra,
valida el formato de lo que se escribe y guardará la cola de movimientos
pendientes; no calcula saldos ni decide nada contable.

Hoy es el esqueleto: arranque, tema, reglas del analizador, el test que impone
la arquitectura y `Money`. Todavía no hay pantallas ni red.

## Comandos (desde `mobile/`)

```bash
flutter pub get
flutter analyze --fatal-infos   # sin avisos: en CI un aviso es un error
flutter test
```

Hace falta Flutter estable 3.47.9 (la versión que fija el trabajo `mobile` de
CI). Compilar para iOS exige un Mac: el proyecto nace con las dos plataformas,
pero CI solo corre el analizador y los tests, que no dependen de ninguna.

## Arquitectura

MVVM por funcionalidad con Riverpod, en dos capas —interfaz (pantallas y view
models) y datos (repositorios y servicios)—, organizada por funcionalidad y no
por capa. `test/architecture_test.dart` impone los límites entre carpetas, como
el ArchUnit del servidor: una funcionalidad no importa el interior de otra y un
widget no habla con la API ni con la base local.

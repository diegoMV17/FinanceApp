import 'dart:io';
import 'dart:math';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:sqlite3/sqlite3.dart';

import 'secure_store.dart';

part 'local_database.g.dart';

/// La base local de la app, cifrada.
///
/// Todavía sin tablas: la cola de pendientes llega en el 7c y la última copia
/// de lo leído después. Lo que existe desde ya es lo que no se puede agregar
/// más tarde sin migrar datos en claro: que el archivo nazca cifrado.
@DriftDatabase(tables: <Type>[])
class LocalDatabase extends _$LocalDatabase {
  LocalDatabase(super.executor);

  @override
  int get schemaVersion => 1;
}

/// El motor no es SQLite3MultipleCiphers: `PRAGMA key` se ignoraría sin un
/// error y la base quedaría en claro. La app no continúa.
class EncryptionUnavailable implements Exception {
  const EncryptionUnavailable();

  @override
  String toString() => 'EncryptionUnavailable';
}

/// Hay un archivo de base y no se puede leer con la clave guardada: la clave
/// se perdió (se borraron los datos de la app a medias) o es otra. No se
/// recrea en silencio: lo que tenga dentro son pendientes sin enviar, y quien
/// decide perderlos es el usuario.
class LocalDatabaseUnreadable implements Exception {
  const LocalDatabaseUnreadable();

  @override
  String toString() => 'LocalDatabaseUnreadable';
}

/// Lo mínimo de una base cruda que necesita [unlock]: así se prueba sin el
/// motor, incluido el caso en que el motor no cifra.
abstract interface class RawDatabase {
  List<Map<String, Object?>> select(String sql);

  void execute(String sql);
}

final RegExp _hexKey = RegExp(r'^[0-9a-f]{64}$');

/// Comprueba el cifrado, pone la clave y comprueba que la base se lee.
///
/// En tiempo de ejecución y no con un `assert` como en los ejemplos de la
/// documentación: un `assert` desaparece en release, que es justo donde
/// importa. La clave va en hexadecimal (`PRAGMA hexkey`): sin comillas que
/// escapar, y una que no tenga esa forma no llega al motor.
void unlock(RawDatabase db, String hexKey) {
  if (!_hexKey.hasMatch(hexKey)) {
    throw ArgumentError('la clave de la base no tiene la forma esperada');
  }
  if (db.select('PRAGMA cipher;').isEmpty) {
    throw const EncryptionUnavailable();
  }
  db.execute("PRAGMA hexkey = '$hexKey';");
  try {
    db.select('SELECT count(*) FROM sqlite_master;');
  } on SqliteException {
    throw const LocalDatabaseUnreadable();
  }
}

/// Abre la base cifrada, generando su clave la primera vez.
class LocalDatabaseOpener {
  LocalDatabaseOpener(this._secure, this._directory);

  static const String keyName = 'local_database_key';
  static const String fileName = 'finance.db';

  final SecureStore _secure;

  /// Dónde va el archivo. En la app, el directorio de soporte (no el de
  /// documentos: ese lo ve el usuario y, en iOS, la app Archivos).
  final Future<Directory> Function() _directory;

  File file(Directory directory) => File('${directory.path}/$fileName');

  /// La base abierta y comprobada: si algo falla, falla aquí, antes de que
  /// nadie la use.
  ///
  /// La comprobación corre primero en este isolate con una conexión cruda, y
  /// después la base de verdad se abre en segundo plano, donde [unlock] vuelve
  /// a correr. Así los errores llegan con su tipo y no envueltos por el
  /// isolate.
  Future<LocalDatabase> open() async {
    final String key = await _key();
    final File database = file(await _directory());
    final Database check = sqlite3.open(database.path);
    try {
      unlock(_Sqlite(check), key);
    } finally {
      check.close();
    }
    return LocalDatabase(
      NativeDatabase.createInBackground(
        database,
        setup: (Database raw) => unlock(_Sqlite(raw), key),
      ),
    );
  }

  /// Borra la base local y su clave. Solo cuando el usuario lo confirmó: se
  /// pierde todo lo que no se haya enviado.
  Future<void> erase() async {
    final Directory directory = await _directory();
    for (final String suffix in <String>['', '-wal', '-shm', '-journal']) {
      final File part = File('${file(directory).path}$suffix');
      if (part.existsSync()) {
        part.deleteSync();
      }
    }
    await _secure.delete(keyName);
  }

  /// 32 bytes de un generador seguro, en hexadecimal, guardados en el
  /// almacenamiento seguro. Nunca una clave fija: es el error más fácil de
  /// copiar de los ejemplos.
  Future<String> _key() async {
    final String? stored = await _secure.read(keyName);
    if (stored != null) {
      return stored;
    }
    final Random random = Random.secure();
    final String generated = <String>[
      for (int i = 0; i < 32; i++)
        random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ].join();
    await _secure.write(keyName, generated);
    return generated;
  }
}

class _Sqlite implements RawDatabase {
  _Sqlite(this._db);

  final Database _db;

  @override
  List<Map<String, Object?>> select(String sql) => <Map<String, Object?>>[
    for (final Row row in _db.select(sql)) <String, Object?>{...row},
  ];

  @override
  void execute(String sql) => _db.execute(sql);
}

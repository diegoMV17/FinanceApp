import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/storage/local_database.dart';

import '../../support/fake_secure_store.dart';

/// La base local, abierta de verdad: estos tests usan el SQLite que empaqueta
/// el paquete sqlite3 para la máquina que los corre (los hooks del pubspec
/// eligen SQLite3MultipleCiphers también aquí), así que prueban el cifrado y no
/// una imitación.
void main() {
  late Directory dir;
  late FakeSecureStore secure;
  late LocalDatabaseOpener opener;
  final List<LocalDatabase> opened = <LocalDatabase>[];

  Future<LocalDatabase> open() async {
    final LocalDatabase db = await opener.open();
    opened.add(db);
    return db;
  }

  Future<void> closeAll() async {
    for (final LocalDatabase db in opened) {
      await db.close();
    }
    opened.clear();
  }

  setUp(() {
    dir = Directory.systemTemp.createTempSync('finance_db_');
    secure = FakeSecureStore();
    opener = LocalDatabaseOpener(secure, () async => dir);
  });

  tearDown(() async {
    await closeAll();
    dir.deleteSync(recursive: true);
  });

  test('abre, y el motor es el que cifra', () async {
    final LocalDatabase db = await open();

    final List<QueryRow> cipher = await db.customSelect('PRAGMA cipher;').get();

    expect(cipher, isNotEmpty);
  });

  test('el archivo no es SQLite en claro', () async {
    final LocalDatabase db = await open();
    await db.customStatement('CREATE TABLE probe (x TEXT)');
    await db.customStatement("INSERT INTO probe VALUES ('Almuerzo')");
    await closeAll();

    final List<int> bytes = opener.file(dir).readAsBytesSync();

    // Todo archivo de SQLite sin cifrar empieza así.
    expect(latin1.decode(bytes.sublist(0, 15)), isNot('SQLite format 3'));
    expect(latin1.decode(bytes), isNot(contains('Almuerzo')));
  });

  test(
    'abrir ya deja el archivo escrito y cifrado, sin tablas ni datos',
    () async {
      // Visto en un teléfono: abierta de forma perezosa, la base medía 0 bytes.
      // Un archivo vacío no es cifrado: mirar su cabecera no probaba nada, y el
      // motor del isolate de la base no había corrido todavía.
      await open();
      await closeAll();

      final List<int> bytes = opener.file(dir).readAsBytesSync();

      expect(bytes, isNotEmpty);
      expect(latin1.decode(bytes.sublist(0, 15)), isNot('SQLite format 3'));
    },
  );

  test(
    'la clave son 32 bytes al azar, se genera una vez y se reutiliza',
    () async {
      final LocalDatabase first = await open();
      await first.customStatement('CREATE TABLE probe (x INTEGER)');
      await closeAll();
      final String? key = secure.values[LocalDatabaseOpener.keyName];

      final LocalDatabase again = await open();
      final List<QueryRow> tables = await again
          .customSelect("SELECT name FROM sqlite_master WHERE name = 'probe'")
          .get();

      expect(key, matches(RegExp(r'^[0-9a-f]{64}$')));
      expect(secure.values[LocalDatabaseOpener.keyName], key);
      expect(tables, hasLength(1));
    },
  );

  test('dos instalaciones no comparten clave', () async {
    await open();
    final String? first = secure.values[LocalDatabaseOpener.keyName];
    await closeAll();

    final FakeSecureStore other = FakeSecureStore();
    final Directory otherDir = Directory.systemTemp.createTempSync('finance_');
    final LocalDatabase db = await LocalDatabaseOpener(
      other,
      () async => otherDir,
    ).open();
    await db.close();
    otherDir.deleteSync(recursive: true);

    expect(other.values[LocalDatabaseOpener.keyName], isNot(first));
  });

  test(
    'con otra clave no abre: no es una base nueva, es una ilegible',
    () async {
      final LocalDatabase db = await open();
      await db.customStatement('CREATE TABLE probe (x INTEGER)');
      await closeAll();
      secure.values[LocalDatabaseOpener.keyName] = 'ab' * 32;

      await expectLater(open(), throwsA(isA<LocalDatabaseUnreadable>()));
    },
  );

  test('si se perdió la clave, la base que quedó tampoco abre', () async {
    final LocalDatabase db = await open();
    await db.customStatement('CREATE TABLE probe (x INTEGER)');
    await closeAll();
    secure.values.clear();

    await expectLater(open(), throwsA(isA<LocalDatabaseUnreadable>()));
  });

  test('borrar la base local deja empezar de cero', () async {
    final LocalDatabase db = await open();
    await db.customStatement('CREATE TABLE probe (x INTEGER)');
    await closeAll();
    secure.values.clear();

    await opener.erase();
    final LocalDatabase fresh = await open();
    final List<QueryRow> tables = await fresh
        .customSelect("SELECT name FROM sqlite_master WHERE name = 'probe'")
        .get();

    expect(tables, isEmpty);
  });

  group('sin el motor que cifra, no continúa', () {
    test('si PRAGMA cipher no responde, ni siquiera pone la clave', () {
      final _FakeRaw raw = _FakeRaw(cipher: false);

      expect(
        () => unlock(raw, 'ab' * 32),
        throwsA(isA<EncryptionUnavailable>()),
      );
      expect(raw.executed, isEmpty);
    });

    test('con el motor, pone la clave en hexadecimal y comprueba que lee', () {
      final _FakeRaw raw = _FakeRaw(cipher: true);

      unlock(raw, 'ab' * 32);

      expect(raw.executed, <String>["PRAGMA hexkey = '${'ab' * 32}';"]);
      expect(raw.selected, contains('SELECT count(*) FROM sqlite_master;'));
    });

    test('una clave que no es hexadecimal de 64 no llega al motor', () {
      final _FakeRaw raw = _FakeRaw(cipher: true);

      expect(() => unlock(raw, "x'; --"), throwsArgumentError);
      expect(raw.executed, isEmpty);
    });
  });
}

/// Una base cruda falsa: dice si tiene el motor de cifrado y anota lo que se
/// le pide.
class _FakeRaw implements RawDatabase {
  _FakeRaw({required this.cipher});

  final bool cipher;
  final List<String> executed = <String>[];
  final List<String> selected = <String>[];

  @override
  List<Map<String, Object?>> select(String sql) {
    selected.add(sql);
    if (sql.startsWith('PRAGMA cipher')) {
      return cipher
          ? <Map<String, Object?>>[
              <String, Object?>{'cipher': 'chacha20'},
            ]
          : <Map<String, Object?>>[];
    }
    return <Map<String, Object?>>[
      <String, Object?>{'count(*)': 0},
    ];
  }

  @override
  void execute(String sql) => executed.add(sql);
}

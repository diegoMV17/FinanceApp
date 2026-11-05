import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/storage/local_database.dart';
import 'package:mobile/data/local/local_database.dart';

import '../../support/fake_secure_store.dart';

/// El esquema de la base, sobre un archivo cifrado de verdad. La migración no
/// es teórica: los teléfonos del 7b ya tienen una base en la versión 1, y
/// borrarla no es una opción.
void main() {
  late Directory dir;
  late LocalDatabaseOpener opener;
  final List<GeneratedDatabase> opened = <GeneratedDatabase>[];

  Future<D> open<D extends GeneratedDatabase>(
    D Function(QueryExecutor) build,
  ) async {
    final D db = await opener.open(build);
    opened.add(db);
    return db;
  }

  Future<void> closeAll() async {
    for (final GeneratedDatabase db in opened) {
      await db.close();
    }
    opened.clear();
  }

  Future<List<String>> objects(GeneratedDatabase db) async {
    final List<QueryRow> rows = await db
        .customSelect('SELECT name FROM sqlite_master')
        .get();
    return rows.map((QueryRow row) => row.read<String>('name')).toList();
  }

  Future<int> userVersion(GeneratedDatabase db) async {
    final QueryRow row = await db
        .customSelect('PRAGMA user_version')
        .getSingle();
    return row.read<int>('user_version');
  }

  setUp(() {
    dir = Directory.systemTemp.createTempSync('finance_schema_');
    opener = LocalDatabaseOpener(FakeSecureStore(), () async => dir);
  });

  tearDown(() async {
    await closeAll();
    dir.deleteSync(recursive: true);
  });

  test('una base nueva nace con la cola', () async {
    final LocalDatabase db = await open(LocalDatabase.new);

    expect(await objects(db), contains('pending_movements'));
    expect(await userVersion(db), 2);
  });

  test(
    'la base de la versión 1 que ya hay en el teléfono gana la cola',
    () async {
      await open(_FirstVersion.new);
      await closeAll();
      expect(opener.file(dir).lengthSync(), greaterThan(0));

      final LocalDatabase db = await open(LocalDatabase.new);

      expect(await objects(db), contains('pending_movements'));
      expect(await userVersion(db), 2);
    },
  );

  group(
    'lo encolado no cambia: reintentar con otro contenido sería un 409',
    () {
      Future<LocalDatabase> withARow() async {
        final LocalDatabase db = await open(LocalDatabase.new);
        await db.customStatement(
          'INSERT INTO pending_movements (id, body, status) '
          "VALUES ('a', 'cuerpo', 'waiting')",
        );
        return db;
      }

      for (final (String field, String value) in <(String, String)>[
        ('body', "'otro'"),
        ('id', "'b'"),
        ('position', '99'),
      ]) {
        test('ni su $field', () async {
          final LocalDatabase db = await withARow();

          await expectLater(
            db.customStatement('UPDATE pending_movements SET $field = $value'),
            throwsA(anything),
          );
        });
      }

      test('su estado sí', () async {
        final LocalDatabase db = await withARow();

        await db.customStatement(
          "UPDATE pending_movements SET status = 'conflict'",
        );
      });

      test('y también en la base que vino de la versión 1', () async {
        await open(_FirstVersion.new);
        await closeAll();
        final LocalDatabase db = await withARow();

        await expectLater(
          db.customStatement("UPDATE pending_movements SET body = 'otro'"),
          throwsA(anything),
        );
      });
    },
  );

  test('el archivo sigue cifrado con la cola adentro', () async {
    final LocalDatabase db = await open(LocalDatabase.new);
    await db.customStatement(
      'INSERT INTO pending_movements (id, body, status) '
      "VALUES ('a', 'Almuerzo con Juan', 'waiting')",
    );
    await closeAll();

    final List<int> bytes = opener.file(dir).readAsBytesSync();

    expect(latin1.decode(bytes.sublist(0, 15)), isNot('SQLite format 3'));
    expect(latin1.decode(bytes), isNot(contains('Almuerzo')));
  });
}

/// La base tal como la dejó el 7b: versión 1, sin tablas.
class _FirstVersion extends GeneratedDatabase {
  _FirstVersion(super.executor);

  @override
  int get schemaVersion => 1;

  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      const <TableInfo<Table, Object?>>[];
}

import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/storage/local_database.dart';
import 'package:mobile/data/local/local_database.dart';
import 'package:mobile/data/local/pending_movements.dart';

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

    expect(
      await objects(db),
      containsAll(<String>['pending_movements', 'read_snapshots']),
    );
    expect(await userVersion(db), 3);
  });

  test(
    'la base de la versión 1 que ya hay en el teléfono gana la cola y la copia',
    () async {
      await open(_FirstVersion.new);
      await closeAll();
      expect(opener.file(dir).lengthSync(), greaterThan(0));

      final LocalDatabase db = await open(LocalDatabase.new);

      expect(
        await objects(db),
        containsAll(<String>['pending_movements', 'read_snapshots']),
      );
      expect(await userVersion(db), 3);
    },
  );

  test(
    'la de la versión 2 gana la última copia y no pierde sus pendientes',
    () async {
      final _SecondVersion old = await open(_SecondVersion.new);
      await old.customStatement(
        'INSERT INTO pending_movements (id, body, status) '
        "VALUES ('a', 'cuerpo', 'waiting')",
      );
      await closeAll();

      final LocalDatabase db = await open(LocalDatabase.new);

      expect(await objects(db), contains('read_snapshots'));
      expect(await userVersion(db), 3);
      final List<QueryRow> rows = await db
          .customSelect('SELECT id, body FROM pending_movements')
          .get();
      expect(rows.single.read<String>('body'), 'cuerpo');
      // Y lo encolado sigue sin poder cambiar.
      await expectLater(
        db.customStatement("UPDATE pending_movements SET body = 'otro'"),
        throwsA(anything),
      );
    },
  );

  group('la última copia solo se reemplaza por una lectura más nueva', () {
    Future<LocalDatabase> withASnapshot() async {
      final LocalDatabase db = await open(LocalDatabase.new);
      await db.customStatement(
        'INSERT INTO read_snapshots (key, body, read_at) '
        "VALUES ('balances', '{}', 1000)",
      );
      return db;
    }

    test('una más nueva, sí', () async {
      final LocalDatabase db = await withASnapshot();

      await db.customStatement(
        "UPDATE read_snapshots SET body = '[]', read_at = 2000",
      );
    });

    test(
      'una más vieja, no: una respuesta lenta no tapa la de después',
      () async {
        final LocalDatabase db = await withASnapshot();

        await expectLater(
          db.customStatement(
            "UPDATE read_snapshots SET body = '[]', read_at = 999",
          ),
          throwsA(anything),
        );
      },
    );

    test('ni el cuerpo sin una lectura nueva', () async {
      final LocalDatabase db = await withASnapshot();

      await expectLater(
        db.customStatement("UPDATE read_snapshots SET body = '[]'"),
        throwsA(anything),
      );
    });

    test('ni otra clave', () async {
      final LocalDatabase db = await withASnapshot();

      await expectLater(
        db.customStatement(
          "UPDATE read_snapshots SET key = 'people', read_at = 2000",
        ),
        throwsA(anything),
      );
    });

    test('también en la base que vino de la versión 2', () async {
      await open(_SecondVersion.new);
      await closeAll();
      final LocalDatabase db = await withASnapshot();

      await expectLater(
        db.customStatement(
          "UPDATE read_snapshots SET body = '[]', read_at = 999",
        ),
        throwsA(anything),
      );
    });
  });

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

/// La base tal como la dejó el 7c: versión 2, con la cola y su trigger.
class _SecondVersion extends GeneratedDatabase {
  _SecondVersion(super.executor);

  late final $PendingMovementsTable pendingMovements = $PendingMovementsTable(
    this,
  );

  @override
  int get schemaVersion => 2;

  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      <TableInfo<Table, Object?>>[pendingMovements];

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (Migrator migrator) async {
      await migrator.createAll();
      await customStatement(pendingMovementsKeepTheirContent);
    },
  );
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

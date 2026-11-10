import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/data/local/local_database.dart';
import 'package:mobile/data/local/read_snapshots_store.dart';

/// El almacén de la última copia: guarda lo leído con su hora y lo devuelve.
void main() {
  late LocalDatabase db;
  late ReadSnapshotsStore store;

  final DateTime nine = DateTime.utc(2026, 11, 9, 9);
  final DateTime ten = DateTime.utc(2026, 11, 9, 10);

  setUp(() {
    db = LocalDatabase(NativeDatabase.memory());
    store = ReadSnapshotsStore(db);
  });

  tearDown(() => db.close());

  test('sin nada leído, no hay copia', () async {
    expect(await store.read('balances'), isNull);
  });

  test('devuelve lo guardado con la hora en que se leyó', () async {
    await store.save('balances', '{"a":1}', nine);

    final ReadSnapshot? copy = await store.read('balances');

    expect(copy!.body, '{"a":1}');
    expect(copy.readAt, nine);
  });

  test('una lectura más nueva reemplaza a la anterior', () async {
    await store.save('balances', '{"a":1}', nine);
    await store.save('balances', '{"a":2}', ten);

    expect((await store.read('balances'))!.body, '{"a":2}');
  });

  test('una respuesta lenta que llega después no tapa la más nueva', () async {
    await store.save('balances', '{"a":2}', ten);
    await store.save('balances', '{"a":1}', nine);

    final ReadSnapshot copy = (await store.read('balances'))!;
    expect(copy.body, '{"a":2}');
    expect(copy.readAt, ten);
  });

  test('cada clave es su propia copia', () async {
    await store.save('balances', 'b', nine);
    await store.save('budgets/2026-11', 'p', nine);

    expect((await store.read('balances'))!.body, 'b');
    expect((await store.read('budgets/2026-11'))!.body, 'p');
  });

  test('un fallo de la base no sale con su texto: trae el cuerpo', () async {
    await db.customStatement('DROP TABLE read_snapshots');

    await expectLater(
      store.save('balances', 'Almuerzo con Juan', nine),
      throwsA(
        isA<SnapshotNotSaved>().having(
          (SnapshotNotSaved e) => e.toString(),
          'toString',
          isNot(contains('Almuerzo')),
        ),
      ),
    );
  });
}

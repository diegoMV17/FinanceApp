import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/storage/local_database.dart';
import 'package:mobile/data/api/export.dart';
import 'package:mobile/data/local/local_database.dart';
import 'package:mobile/data/local/pending_movements.dart';
import 'package:mobile/data/local/pending_movements_store.dart';
import 'package:mobile/data/sync/movement_id.dart';
import 'package:mobile/data/sync/pending_movement.dart';

import '../../support/fake_secure_store.dart';

void main() {
  late LocalDatabase db;
  late PendingMovementsStore store;

  PendingMovement expense(String id, {int cents = 1234}) => PendingMovement.of(
    MovementRequestExpenseRequest(
      type: ExpenseRequestType.expense,
      id: id,
      date: '2026-11-02',
      description: 'Almuerzo',
      paidFrom: '0a3c0f5e-2b1d-4c8e-9f00-00000000000a',
      category: '0a3c0f5e-2b1d-4c8e-9f00-00000000000b',
      amountInCents: cents,
    ),
  );

  const String first = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String second = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';
  const String third = '0a3c0f5e-2b1d-4c8e-9f00-000000000003';
  const String conflictType = 'urn:finance:error:duplicate-id';

  Future<List<String>> waitingIds() async => <String>[
    for (final PendingMovement pending in await store.waiting())
      pending.id.value,
  ];

  setUp(() {
    db = LocalDatabase(NativeDatabase.memory());
    store = PendingMovementsStore(db);
  });

  tearDown(() => db.close());

  test('salen en el orden en que entraron', () async {
    await store.enqueue(expense(second));
    await store.enqueue(expense(third));
    await store.enqueue(expense(first));

    expect(await waitingIds(), <String>[second, third, first]);
  });

  test('sale el cuerpo exacto que entró', () async {
    final PendingMovement movement = expense(first);

    await store.enqueue(movement);

    expect((await store.waiting()).single.body, movement.body);
  });

  group('encolar dos veces el mismo id', () {
    test('con el mismo contenido es un doble toque: queda uno', () async {
      await store.enqueue(expense(first));
      await store.enqueue(expense(first));

      expect(await waitingIds(), <String>[first]);
    });

    test('con otro contenido no se pierde en silencio: falla', () async {
      await store.enqueue(expense(first));

      await expectLater(
        store.enqueue(expense(first, cents: 999)),
        throwsA(isA<ConflictingPendingMovement>()),
      );
      expect((await store.waiting()).single.body, expense(first).body);
    });
  });

  test('sale de la cola al quitarlo, y los demás siguen en orden', () async {
    await store.enqueue(expense(first));
    await store.enqueue(expense(second));
    await store.enqueue(expense(third));

    await store.remove(MovementId.parse(second));

    expect(await waitingIds(), <String>[first, third]);
  });

  group('lo que el servidor rechazó', () {
    test('un conflicto no se vuelve a enviar y guarda el type', () async {
      await store.enqueue(expense(first));

      await store.markConflict(MovementId.parse(first), conflictType);

      expect(await store.waiting(), isEmpty);
      final PendingMovementRow row = await db
          .select(db.pendingMovements)
          .getSingle();
      expect(row.status, PendingStatus.conflict);
      expect(row.problemType, conflictType);
    });

    test('un rechazo tampoco', () async {
      await store.enqueue(expense(first));

      await store.markRejected(MovementId.parse(first), null);

      expect(await store.waiting(), isEmpty);
      final PendingMovementRow row = await db
          .select(db.pendingMovements)
          .getSingle();
      expect(row.status, PendingStatus.rejected);
      expect(row.problemType, isNull);
    });
  });

  group('descartar', () {
    test('lo que necesita atención, se puede', () async {
      await store.enqueue(expense(first));
      await store.markConflict(MovementId.parse(first), conflictType);

      expect(await store.discard(MovementId.parse(first)), isTrue);

      expect(await store.counts.first, const PendingCounts(0, 0));
    });

    test('lo que espera su envío, no: se perdería un gasto', () async {
      await store.enqueue(expense(first));

      expect(await store.discard(MovementId.parse(first)), isFalse);

      expect(await waitingIds(), <String>[first]);
    });
  });

  group('el conteo que se ve en la interfaz', () {
    test('vacío, cero y cero', () async {
      expect(await store.counts.first, const PendingCounts(0, 0));
    });

    test('separa lo que espera de lo que necesita atención', () async {
      await store.enqueue(expense(first));
      await store.enqueue(expense(second));
      await store.enqueue(expense(third));
      await store.markConflict(MovementId.parse(second), conflictType);
      await store.markRejected(MovementId.parse(third), null);

      expect(await store.counts.first, const PendingCounts(1, 2));
    });

    test('avisa cuando cambia', () async {
      final Future<List<PendingCounts>> seen = store.counts.take(3).toList();
      await Future<void>.delayed(Duration.zero);
      await store.enqueue(expense(first));
      await Future<void>.delayed(Duration.zero);
      await store.remove(MovementId.parse(first));

      expect(await seen, const <PendingCounts>[
        PendingCounts(0, 0),
        PendingCounts(1, 0),
        PendingCounts(0, 0),
      ]);
    });
  });

  test('sigue ahí, en orden, después de cerrar la app', () async {
    final Directory dir = Directory.systemTemp.createTempSync('finance_queue_');
    addTearDown(() => dir.deleteSync(recursive: true));
    final LocalDatabaseOpener opener = LocalDatabaseOpener(
      FakeSecureStore(),
      () async => dir,
    );
    final LocalDatabase before = await opener.open(LocalDatabase.new);
    final PendingMovementsStore written = PendingMovementsStore(before);
    await written.enqueue(expense(second));
    await written.enqueue(expense(first));
    await before.close();

    final LocalDatabase after = await opener.open(LocalDatabase.new);
    addTearDown(after.close);

    expect(
      <String>[
        for (final PendingMovement pending in await PendingMovementsStore(
          after,
        ).waiting())
          pending.id.value,
      ],
      <String>[second, first],
    );
  });

  test('una fila que ya no se puede leer no frena a las demás', () async {
    // Una versión futura de la app, o una fila dañada. Enviarla es imposible;
    // lo honesto es que quede a la vista, no perderla ni bloquear la cola.
    await db.customStatement(
      'INSERT INTO pending_movements (id, body, status) '
      "VALUES ('$second', 'esto no es un movimiento', 'waiting')",
    );
    await store.enqueue(expense(first));

    expect(await waitingIds(), <String>[first]);
    expect(await store.counts.first, const PendingCounts(1, 1));
  });
}

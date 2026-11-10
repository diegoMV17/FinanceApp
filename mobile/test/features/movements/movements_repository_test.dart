import 'dart:convert';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/movement_draft.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/data/catalog/account_catalog.dart';
import 'package:mobile/data/local/local_database.dart';
import 'package:mobile/data/local/pending_movements_store.dart';
import 'package:mobile/data/sync/pending_movement.dart';
import 'package:mobile/data/sync/pending_queue.dart';
import 'package:mobile/features/movements/data/movements_repository.dart';

import '../../support/server_fixture.dart';

/// Registrar: el movimiento entra a la cola cifrada y de ahí sale. Nada
/// aquí habla con el servidor.
void main() {
  const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String nequi = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';
  const String food = '0a3c0f5e-2b1d-4c8e-9f00-000000000003';

  late ServerFixture server;
  late LocalDatabase db;
  late PendingMovementsStore store;
  late int wakes;
  late MovementsRepository repository;

  ExpenseDraft lunch() => ExpenseDraft(
    id: id,
    date: CalendarDate(2026, 11, 9),
    amount: Money.ofCents(1250000),
    description: 'Almuerzo',
    paidFrom: nequi,
    category: food,
  );

  setUp(() {
    server = ServerFixture();
    db = LocalDatabase(NativeDatabase.memory());
    store = PendingMovementsStore(db);
    wakes = 0;
    repository = MovementsRepository(
      PendingQueue(store, () async => wakes++),
      AccountCatalog(server.reader),
    );
  });

  tearDown(() async {
    await db.close();
    await server.close();
  });

  test('queda en la cola, traducido, y despierta el envío', () async {
    await repository.record(lunch());

    final PendingMovement queued = (await store.waiting()).single;
    expect(queued.id.value, id);
    final Map<String, Object?> body =
        jsonDecode(queued.body) as Map<String, Object?>;
    expect(body['paidFrom'], nequi);
    expect(body['category'], food);
    expect(body['amountInCents'], 1250000);
    expect(wakes, 1);
    // Nada salió a la red: eso lo hace el envío de la cola.
    expect(server.adapter.requests, isEmpty);
  });

  test('el mismo borrador dos veces (un doble toque) deja uno', () async {
    await repository.record(lunch());
    await repository.record(lunch());

    expect(await store.waiting(), hasLength(1));
  });

  test('si no se pudo guardar, se dice: ese gasto no quedó', () async {
    await db.customStatement('DROP TABLE pending_movements');

    await expectLater(
      repository.record(lunch()),
      throwsA(isA<MovementNotRecorded>()),
    );
    expect(wakes, 0);
  });

  test('para elegir, solo las cuentas abiertas', () async {
    server.answer('GET /api/balances', 200, <String, Object?>{
      'asOf': '2026-11-09',
      'accounts': <Object?>[
        <String, Object?>{
          'id': nequi,
          'name': 'Nequi',
          'type': 'ASSET',
          'balanceInCents': 0,
        },
        <String, Object?>{
          'id': food,
          'name': 'Vieja',
          'type': 'EXPENSE',
          'balanceInCents': 0,
          'archivedAt': '2026-01-01T00:00:00Z',
        },
      ],
    });

    final List<AccountRef> accounts = (await repository.openAccounts()).value;

    expect(accounts.map((AccountRef a) => a.id), <String>[nequi]);
  });
}

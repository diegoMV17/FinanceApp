import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/ledger/book_changes.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/data/local/pending_movements_store.dart';
import 'package:mobile/data/sync/pending_movement.dart';
import 'package:mobile/features/dashboard/data/dashboard.dart';
import 'package:mobile/features/dashboard/data/waiting_movements.dart';
import 'package:mobile/features/dashboard/ui/dashboard_view_model.dart';

import '../../support/app_container.dart';
import '../../support/movement_samples.dart';
import '../../support/server_fixture.dart';

void main() {
  late ServerFixture server;
  late ProviderContainer container;

  Map<String, Object?> summary() => <String, Object?>{
    'asOf': '2026-11-09',
    'monthFrom': '2026-11-01',
    'moneyReallyMineInCents': 150000000,
    'incomeThatMonthInCents': 0,
    'expensesThatMonthInCents': 0,
    'totalsByTypeInCents': <String, Object?>{},
  };

  setUp(() {
    server = ServerFixture()
      ..answer('GET /api/summary', 200, summary())
      ..answer('GET /api/balances', 200, <String, Object?>{
        'asOf': '2026-11-09',
        'accounts': <Object?>[],
      })
      ..answer('GET /api/stats/over-time', 200, <String, Object?>{
        'from': '2025-11-09',
        'to': '2026-11-09',
        'points': <Object?>[summary()],
      })
      ..answer('GET /api/stats/by-account', 200, <String, Object?>{
        'from': '2026-11-01',
        'to': '2026-11-09',
        'accounts': <Object?>[],
      });
    container = appContainer(server)
      ..listen(dashboardViewModelProvider, (_, _) {});
  });

  tearDown(() async {
    container.dispose();
    await server.close();
  });

  Future<Reading<Dashboard>?> dashboard() =>
      container.read(dashboardViewModelProvider.future);

  test('bloqueada, nada sale', () async {
    expect(await dashboard(), isNull);
    expect(server.adapter.requests, isEmpty);
  });

  test('desbloqueada, las cifras del servidor', () async {
    await unlock(container);

    expect(
      (await dashboard())!.value.summary.moneyReallyMine,
      Money.ofCents(150000000),
    );
  });

  test('cuando el servidor acepta un movimiento, se vuelve a pedir', () async {
    await unlock(container);
    await dashboard();
    final int before = server.adapter.requests.length;

    container.read(bookChangesProvider.notifier).changed();
    await dashboard();

    expect(server.adapter.requests.length, before * 2);
  });

  test('sin conexión, la copia, con el porqué', () async {
    await unlock(container);
    await dashboard();
    server.offline = true;

    container.read(dashboardViewModelProvider.notifier).refresh();
    final Reading<Dashboard> reading = (await dashboard())!;

    expect(reading.staleBecause, const RequestFailure(FailureKind.noAnswer));
  });

  test('cuántos pendientes todavía no cuentan en las cifras', () async {
    final PendingMovementsStore store = await container.read(
      pendingMovementsStoreProvider.future,
    );
    container.listen(waitingMovementsProvider, (_, _) {});
    expect(await container.read(waitingMovementsProvider.future), 0);

    await store.enqueue(
      PendingMovement.restored(
        sampleId,
        '{"type":"EXPENSE","id":"$sampleId","date":"2026-11-09",'
        '"amountInCents":1,"description":"","paidFrom":"$sampleId",'
        '"category":"$sampleId"}',
      ),
    );

    // El contador de Drift avisa en otro turno del bucle.
    for (
      int i = 0;
      i < 50 && container.read(waitingMovementsProvider).value != 1;
      i++
    ) {
      await Future<void>.delayed(const Duration(milliseconds: 10));
    }
    expect(container.read(waitingMovementsProvider).value, 1);
  });
}

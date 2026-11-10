import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/dates/clock.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/book_changes.dart';
import 'package:mobile/data/local/pending_movements_store.dart';
import 'package:mobile/data/sync/pending_movement.dart';
import 'package:mobile/data/sync/pending_sender.dart';
import 'package:mobile/data/sync/send_verdict.dart';
import 'package:mobile/features/movements/ui/record_movement_view_model.dart';

import '../../support/app_container.dart';
import '../../support/server_fixture.dart';

/// Registrar, sin widgets: lo que se escribe pasa por el formato, entra a la
/// cola cifrada una sola vez y no cuenta en el libro hasta que el servidor lo
/// acepte.
void main() {
  const String nequi = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String food = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';

  late ServerFixture server;
  late ProviderContainer container;
  late ProviderSubscription<RecordMovementState> form;

  RecordMovementViewModel viewModel() =>
      container.read(recordMovementViewModelProvider.notifier);

  Future<List<PendingMovement>> queued() async =>
      (await container.read(pendingMovementsStoreProvider.future)).waiting();

  setUp(() async {
    server = ServerFixture();
    container = appContainer(
      server,
      overrides: [
        clockProvider.overrideWithValue(() => DateTime(2026, 10, 31, 23, 30)),
        // El envío no corre aquí: se prueba aparte. Lo que importa es qué
        // queda en la cola.
        pendingSenderProvider.overrideWith(_IdleSender.new),
      ],
    );
    await unlock(container);
    form = container.listen(recordMovementViewModelProvider, (_, _) {});
  });

  tearDown(() async {
    container.dispose();
    await server.close();
  });

  test('empieza en gasto, con la fecha del teléfono en su zona', () {
    expect(form.read().kind, MovementKind.expense);
    expect(form.read().date, CalendarDate(2026, 10, 31));
  });

  test(
    'un gasto válido queda en la cola, con cada cuenta en su hueco',
    () async {
      viewModel()
        ..chooseFirst(nequi)
        ..chooseSecond(food);

      await viewModel().submit(amount: '12.500', description: ' Almuerzo ');

      expect(form.read().submit.isDone, isTrue);
      final Map<String, Object?> body =
          jsonDecode((await queued()).single.body) as Map<String, Object?>;
      expect(body['type'], 'EXPENSE');
      expect(body['paidFrom'], nequi);
      expect(body['category'], food);
      expect(body['amountInCents'], 1250000);
      expect(body['description'], 'Almuerzo');
      expect(body['date'], '2026-10-31');
    },
  );

  test('encolado todavía no cambia el libro: eso lo dice el envío', () async {
    viewModel()
      ..chooseFirst(nequi)
      ..chooseSecond(food);

    await viewModel().submit(amount: '1', description: '');

    expect(container.read(bookChangesProvider), 0);
  });

  test(
    'un saldo inicial: la cuenta y el patrimonio, sin descripción',
    () async {
      viewModel()
        ..chooseKind(MovementKind.openingBalance)
        ..chooseFirst(nequi)
        ..chooseSecond(food);

      await viewModel().submit(amount: '1.000.000', description: 'ignorada');

      final Map<String, Object?> body =
          jsonDecode((await queued()).single.body) as Map<String, Object?>;
      expect(body['type'], 'OPENING_BALANCE');
      expect(body['account'], nequi);
      expect(body['equity'], food);
      expect(body.containsKey('description'), isFalse);
    },
  );

  group('lo que no tiene formato no entra a la cola', () {
    test('un monto que no es', () async {
      viewModel()
        ..chooseFirst(nequi)
        ..chooseSecond(food);

      await viewModel().submit(amount: '-5', description: '');

      expect(form.read().submit.error, isNotNull);
      expect(await queued(), isEmpty);
    });

    test('sin elegir de dónde sale', () async {
      viewModel().chooseSecond(food);

      await viewModel().submit(amount: '5', description: '');

      expect(form.read().submit.error, contains('Sale de'));
      expect(await queued(), isEmpty);
    });

    test('una descripción demasiado larga', () async {
      viewModel()
        ..chooseFirst(nequi)
        ..chooseSecond(food);

      await viewModel().submit(amount: '5', description: 'a' * 201);

      expect(form.read().submit.error, contains('200'));
      expect(await queued(), isEmpty);
    });
  });

  test('los atajos de fecha: hoy y ayer, en la zona del teléfono', () {
    // El reloj del test marca el 31 de octubre a las 23:30.
    viewModel().chooseDaysAgo(1);
    expect(form.read().date, CalendarDate(2026, 10, 30));

    viewModel().chooseDaysAgo(0);
    expect(form.read().date, CalendarDate(2026, 10, 31));
  });

  test('«ayer» el 1 de noviembre es el 31 de octubre', () {
    final ProviderContainer november = appContainer(
      server,
      overrides: [
        clockProvider.overrideWithValue(() => DateTime(2026, 11, 1, 0, 30)),
        pendingSenderProvider.overrideWith(_IdleSender.new),
      ],
    );
    addTearDown(november.dispose);
    november.listen(recordMovementViewModelProvider, (_, _) {});

    november.read(recordMovementViewModelProvider.notifier).chooseDaysAgo(1);

    expect(
      november.read(recordMovementViewModelProvider).date,
      CalendarDate(2026, 10, 31),
    );
  });

  test('cambiar de tipo vacía los huecos', () {
    viewModel()
      ..chooseFirst(nequi)
      ..chooseSecond(food)
      ..chooseKind(MovementKind.transfer);

    expect(form.read().first, isNull);
    expect(form.read().second, isNull);
  });

  test('un doble toque deja uno solo en la cola', () async {
    viewModel()
      ..chooseFirst(nequi)
      ..chooseSecond(food);

    await Future.wait(<Future<void>>[
      viewModel().submit(amount: '5', description: ''),
      viewModel().submit(amount: '5', description: ''),
    ]);

    expect(await queued(), hasLength(1));
  });

  test(
    'si no se pudo guardar, se dice, y reintentar usa el mismo id',
    () async {
      final PendingMovementsStore store = await container.read(
        pendingMovementsStoreProvider.future,
      );
      await server.db.customStatement(
        'ALTER TABLE pending_movements RENAME TO pending_movements_off',
      );
      viewModel()
        ..chooseFirst(nequi)
        ..chooseSecond(food);

      await viewModel().submit(amount: '5', description: '');
      expect(form.read().submit.error, contains('no quedó registrado'));

      await server.db.customStatement(
        'ALTER TABLE pending_movements_off RENAME TO pending_movements',
      );
      await viewModel().submit(amount: '5', description: '');

      expect(form.read().submit.isDone, isTrue);
      expect((await store.waiting()).single.id.value, isNotEmpty);
    },
  );

  group('qué se ofrece en cada hueco', () {
    const List<AccountRef> accounts = <AccountRef>[
      AccountRef(id: 'a', name: 'Nequi', kind: AccountKind.asset),
      AccountRef(id: 'e', name: 'Comida', kind: AccountKind.expense),
      AccountRef(id: 'i', name: 'Sueldo', kind: AccountKind.income),
      AccountRef(id: 'q', name: 'Patrimonio', kind: AccountKind.equity),
    ];

    String names(List<AccountRef> refs) =>
        refs.map((AccountRef a) => a.id).join();

    test('gasto: de una cuenta de plata a una categoría de gasto', () {
      expect(names(MovementKind.expense.firstOptions(accounts)), 'a');
      expect(names(MovementKind.expense.secondOptions(accounts)), 'e');
    });

    test('ingreso: a una cuenta de plata desde una de ingreso', () {
      expect(names(MovementKind.income.firstOptions(accounts)), 'a');
      expect(names(MovementKind.income.secondOptions(accounts)), 'i');
    });

    test('transferencia: entre cuentas de plata', () {
      expect(names(MovementKind.transfer.secondOptions(accounts)), 'a');
    });

    test('saldo inicial: contra el patrimonio', () {
      expect(names(MovementKind.openingBalance.secondOptions(accounts)), 'q');
    });
  });
}

/// Un envío que nunca envía, para mirar la cola sin que se vacíe.
class _IdleSender extends PendingSender {
  @override
  PauseReason? build() => null;

  @override
  Future<void> wake() async {}
}

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/ledger/account_balance.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/month_summary.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/data/remote/book_service.dart';
import 'package:mobile/features/dashboard/data/dashboard.dart';
import 'package:mobile/features/dashboard/data/dashboard_repository.dart';

import '../../support/server_fixture.dart';

/// El tablero: lo que dice el servidor, tal cual. La app no suma, no resta y
/// no le cambia el signo a nada.
void main() {
  const String nequi = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String food = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';

  late ServerFixture server;
  late DashboardRepository repository;

  Map<String, Object?> summary(String asOf, {int mine = 150000000}) =>
      <String, Object?>{
        'asOf': asOf,
        'monthFrom': '2026-11-01',
        'moneyReallyMineInCents': mine,
        'incomeThatMonthInCents': 500000000,
        'expensesThatMonthInCents': 1250000,
        'totalsByTypeInCents': <String, Object?>{'ASSET': 151250000},
      };

  void serverAnswersEverything() {
    server
      ..answer('GET /api/summary', 200, summary('2026-11-09'))
      ..answer('GET /api/balances', 200, <String, Object?>{
        'asOf': '2026-11-09',
        'accounts': <Object?>[
          <String, Object?>{
            'id': nequi,
            'name': 'Nequi',
            'type': 'ASSET',
            'balanceInCents': -2500,
          },
        ],
      })
      ..answer('GET /api/stats/over-time', 200, <String, Object?>{
        'from': '2025-11-09',
        'to': '2026-11-09',
        'points': <Object?>[
          summary('2026-10-31', mine: 100000000),
          summary('2026-11-09'),
        ],
      })
      ..answer('GET /api/stats/by-account', 200, <String, Object?>{
        'from': '2026-11-01',
        'to': '2026-11-09',
        'accounts': <Object?>[
          <String, Object?>{
            'id': food,
            'name': 'Alimentación',
            'type': 'EXPENSE',
            'totalInCents': 1250000,
          },
        ],
      });
  }

  setUp(() {
    server = ServerFixture();
    repository = DashboardRepository(BookService(server.reader));
  });

  tearDown(() => server.close());

  test('el resumen del mes, como lo dice el servidor', () async {
    serverAnswersEverything();

    final Dashboard dashboard = (await repository.load()).value;

    expect(dashboard.summary.asOf, CalendarDate(2026, 11, 9));
    expect(dashboard.summary.monthFrom, CalendarDate(2026, 11, 1));
    expect(dashboard.summary.moneyReallyMine, Money.ofCents(150000000));
    expect(dashboard.summary.income, Money.ofCents(500000000));
    expect(dashboard.summary.expenses, Money.ofCents(1250000));
  });

  test('los saldos con su signo: un sobregiro se ve negativo', () async {
    serverAnswersEverything();

    final Dashboard dashboard = (await repository.load()).value;

    expect(dashboard.balances, <AccountBalance>[
      AccountBalance(
        const AccountRef(id: nequi, name: 'Nequi', kind: AccountKind.asset),
        Money.ofCents(-2500),
      ),
    ]);
  });

  test('la serie de doce meses: la elige el servidor', () async {
    serverAnswersEverything();

    final Dashboard dashboard = (await repository.load()).value;

    expect(dashboard.overTime.map((MonthSummary p) => p.asOf), <CalendarDate>[
      CalendarDate(2026, 10, 31),
      CalendarDate(2026, 11, 9),
    ]);
    expect(dashboard.overTime.first.moneyReallyMine, Money.ofCents(100000000));
    // Sin fechas puestas por el teléfono: el año hacia atrás lo decide el
    // servidor, en la zona del dueño.
    final RequestOptions request = server.adapter.requests.firstWhere(
      (RequestOptions r) => r.uri.path == '/api/stats/over-time',
    );
    expect(request.uri.queryParameters, isEmpty);
  });

  test('los gastos por categoría, del mismo mes que el resumen', () async {
    serverAnswersEverything();

    final Dashboard dashboard = (await repository.load()).value;

    expect(dashboard.expensesByCategory.single.category.name, 'Alimentación');
    expect(dashboard.expensesByCategory.single.total, Money.ofCents(1250000));
    final RequestOptions request = server.adapter.requests.firstWhere(
      (RequestOptions r) => r.uri.path == '/api/stats/by-account',
    );
    expect(request.uri.queryParameters, <String, String>{
      'from': '2026-11-01',
      'to': '2026-11-09',
      'type': 'EXPENSE',
    });
  });

  test('sin conexión: la última copia, con su hora y el porqué', () async {
    serverAnswersEverything();
    await repository.load();
    server
      ..offline = true
      ..now = DateTime.utc(2026, 11, 9, 12);

    final Reading<Dashboard> reading = await repository.load();

    expect(reading.isStale, isTrue);
    expect(reading.staleBecause, const RequestFailure(FailureKind.noAnswer));
    expect(reading.readAt, DateTime.utc(2026, 11, 9, 9));
    expect(reading.value.summary.moneyReallyMine, Money.ofCents(150000000));
  });

  test('si una parte falla y tiene copia, el tablero entero avisa', () async {
    serverAnswersEverything();
    await repository.load();
    server
      ..now = DateTime.utc(2026, 11, 9, 12)
      ..answer(
        'GET /api/stats/over-time',
        500,
        apiProblem('internal-error', 500),
      );

    final Reading<Dashboard> reading = await repository.load();

    expect(reading.staleBecause, const RequestFailure(FailureKind.unexpected));
    // La hora que se muestra es la de la parte más vieja.
    expect(reading.readAt, DateTime.utc(2026, 11, 9, 9));
  });

  test('sin conexión y sin copia: el fallo', () async {
    server.offline = true;

    await expectLater(
      repository.load(),
      throwsA(const RequestFailure(FailureKind.noAnswer)),
    );
  });
}

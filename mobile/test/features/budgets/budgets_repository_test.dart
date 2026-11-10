import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/budget.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/data/catalog/account_catalog.dart';
import 'package:mobile/data/remote/budgets_service.dart';
import 'package:mobile/features/budgets/data/budgets_repository.dart';

import '../../support/server_fixture.dart';

void main() {
  const String budgetId = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String food = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';

  late ServerFixture server;
  late BudgetsRepository repository;

  Map<String, Object?> month(String m, {required bool exceeded}) =>
      <String, Object?>{
        'month': m,
        'budgets': <Object?>[
          <String, Object?>{
            'id': budgetId,
            'category': food,
            'categoryName': 'Alimentación',
            'month': m,
            'limitInCents': 100000000,
            'spentInCents': 120000000,
            'remainingInCents': -20000000,
            'percentageUsed': 120,
            'nearTheLimit': true,
            'exceeded': exceeded,
          },
        ],
      };

  setUp(() {
    server = ServerFixture();
    repository = BudgetsRepository(
      BudgetsService(server.gateway, server.reader),
      AccountCatalog(server.reader),
    );
  });

  tearDown(() => server.close());

  test('el mes en curso lo decide el servidor: no se manda mes', () async {
    server.answer('GET /api/budgets', 200, month('2026-11', exceeded: true));

    final MonthBudgets budgets = (await repository.budgets()).value;

    expect(budgets.month, YearMonth(2026, 11));
    expect(server.adapter.last.uri.queryParameters, isEmpty);
  });

  test('las cifras y los avisos, como los decide el servidor', () async {
    // Aun si las cifras sugieren otra cosa: «excedido» lo dice el servidor.
    server.answer('GET /api/budgets', 200, month('2026-11', exceeded: false));

    final BudgetProgress food =
        (await repository.budgets()).value.budgets.single;

    expect(food.categoryName, 'Alimentación');
    expect(food.limit, Money.ofCents(100000000));
    expect(food.spent, Money.ofCents(120000000));
    expect(food.remaining, Money.ofCents(-20000000));
    expect(food.percentageUsed, 120);
    expect(food.nearTheLimit, isTrue);
    expect(food.exceeded, isFalse);
  });

  test('otro mes se pide por su nombre y tiene su propia copia', () async {
    server.answer('GET /api/budgets', 200, month('2026-10', exceeded: true));
    await repository.budgets(YearMonth(2026, 10));
    expect(server.adapter.last.uri.queryParameters, <String, String>{
      'month': '2026-10',
    });

    server.offline = true;
    await expectLater(
      repository.budgets(),
      throwsA(const RequestFailure(FailureKind.noAnswer)),
    );
    expect(
      (await repository.budgets(YearMonth(2026, 10))).value.month,
      YearMonth(2026, 10),
    );
  });

  test(
    'poner un límite manda el id del teléfono, la categoría y el mes',
    () async {
      server.answer('POST /api/budgets', 201, <String, Object?>{
        'id': budgetId,
        'category': food,
        'categoryName': 'Alimentación',
        'month': '2026-11',
        'limitInCents': 50000000,
      });

      await repository.set(
        BudgetDraft(
          id: budgetId,
          categoryId: food,
          month: YearMonth(2026, 11),
          limit: Money.ofCents(50000000),
        ),
      );

      expect(server.sent('POST /api/budgets').single, <String, Object?>{
        'id': budgetId,
        'category': food,
        'month': '2026-11',
        'limitInCents': 50000000,
      });
    },
  );

  test('una respuesta con otro id no se da por buena', () async {
    server.answer('POST /api/budgets', 200, <String, Object?>{
      'id': food,
      'category': food,
      'categoryName': 'Alimentación',
      'month': '2026-11',
      'limitInCents': 50000000,
    });

    await expectLater(
      repository.set(
        BudgetDraft(
          id: budgetId,
          categoryId: food,
          month: YearMonth(2026, 11),
          limit: Money.ofCents(50000000),
        ),
      ),
      throwsA(const RequestFailure(FailureKind.unexpected)),
    );
  });

  test('solo se ofrecen categorías de gasto abiertas', () async {
    server.answer('GET /api/balances', 200, <String, Object?>{
      'asOf': '2026-11-09',
      'accounts': <Object?>[
        <String, Object?>{
          'id': food,
          'name': 'Alimentación',
          'type': 'EXPENSE',
          'balanceInCents': 0,
        },
        <String, Object?>{
          'id': budgetId,
          'name': 'Nequi',
          'type': 'ASSET',
          'balanceInCents': 0,
        },
        <String, Object?>{
          'id': '0a3c0f5e-2b1d-4c8e-9f00-000000000003',
          'name': 'Vieja',
          'type': 'EXPENSE',
          'balanceInCents': 0,
          'archivedAt': '2026-01-01T00:00:00Z',
        },
      ],
    });

    final List<AccountRef> categories = (await repository.categories()).value;

    expect(categories.map((AccountRef a) => a.name), <String>['Alimentación']);
  });

  group('un límite por poner', () {
    test('positivo y dentro del tope', () {
      expect(
        () => BudgetDraft(
          id: budgetId,
          categoryId: food,
          month: YearMonth(2026, 11),
          limit: Money.zero,
        ),
        throwsArgumentError,
      );
    });

    test('con un id v4 del teléfono', () {
      expect(
        () => BudgetDraft(
          id: 'x',
          categoryId: food,
          month: YearMonth(2026, 11),
          limit: Money.ofCents(1),
        ),
        throwsArgumentError,
      );
    });
  });
}

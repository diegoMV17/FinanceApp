import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/forms/submit_state.dart';
import 'package:mobile/core/ledger/book_changes.dart';
import 'package:mobile/features/budgets/ui/budgets_view_model.dart';

import '../../support/app_container.dart';
import '../../support/server_fixture.dart';

void main() {
  const String food = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';

  late ServerFixture server;
  late ProviderContainer container;

  Map<String, Object?> month(String m) => <String, Object?>{
    'month': m,
    'budgets': <Object?>[],
  };

  setUp(() async {
    server = ServerFixture();
    container = appContainer(server);
    await unlock(container);
  });

  tearDown(() async {
    container.dispose();
    await server.close();
  });

  test('elegir otro mes lo pide; volver a null es el mes en curso', () async {
    server.routes['GET /api/budgets'] = (o) =>
        (200, month(o.queryParameters['month'] as String? ?? '2026-11'));
    container.listen(budgetsViewModelProvider, (_, _) {});

    expect(
      (await container.read(budgetsViewModelProvider.future))!.value.month,
      YearMonth(2026, 11),
    );

    container
        .read(selectedBudgetMonthProvider.notifier)
        .show(YearMonth(2026, 9));

    expect(
      (await container.read(budgetsViewModelProvider.future))!.value.month,
      YearMonth(2026, 9),
    );
  });

  group('poner un límite', () {
    late ProviderSubscription<SubmitState> form;

    setUp(() {
      form = container.listen(setBudgetViewModelProvider, (_, _) {});
    });

    Future<void> submit({String? category = food, String limit = '500.000'}) =>
        container
            .read(setBudgetViewModelProvider.notifier)
            .submit(
              categoryId: category,
              month: YearMonth(2026, 11),
              limit: limit,
            );

    test('sin categoría o con un monto que no es, no sale', () async {
      await submit(category: null);
      expect(form.read().error, contains('categoría'));

      await submit(limit: '12.5');
      expect(form.read().error, contains('12.345,67'));

      expect(server.adapter.requests, isEmpty);
    });

    test('el límite viaja en centavos, sin pasar por double', () async {
      server.routes['POST /api/budgets'] = (o) {
        final Map<String, Object?> sent = o.data! as Map<String, Object?>;
        return (
          201,
          <String, Object?>{...sent, 'categoryName': 'Alimentación'},
        );
      };

      await submit(limit: '500.000,10');

      expect(
        (server.sent('POST /api/budgets').single!
            as Map<String, Object?>)['limitInCents'],
        50000010,
      );
      expect(form.read().isDone, isTrue);
      expect(container.read(bookChangesProvider), 1);
    });

    test('un reintento manda el mismo id', () async {
      server.offline = true;
      await submit();
      server.offline = false;
      server.routes['POST /api/budgets'] = (o) => (
        200,
        <String, Object?>{
          ...(o.data! as Map<String, Object?>),
          'categoryName': 'Alimentación',
        },
      );
      await submit();

      final List<Object?> sent = server.sent('POST /api/budgets');
      expect(
        (sent[0]! as Map<String, Object?>)['id'],
        (sent[1]! as Map<String, Object?>)['id'],
      );
    });
  });
}

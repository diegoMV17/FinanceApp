import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/ledger/budget.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/features/budgets/ui/budgets_screen.dart';

import '../../support/screen_doubles.dart';

/// El porcentaje y los avisos son los del servidor: la pantalla no los
/// recalcula aunque las cifras sugieran otra cosa.
void main() {
  BudgetProgress food({required bool near, required bool exceeded}) =>
      BudgetProgress(
        id: 'b',
        categoryId: 'c',
        categoryName: 'Alimentación',
        month: YearMonth(2026, 11),
        limit: Money.ofCents(100000000),
        spent: Money.ofCents(120000000),
        remaining: Money.ofCents(-20000000),
        percentageUsed: 87,
        nearTheLimit: near,
        exceeded: exceeded,
      );

  Future<void> pump(WidgetTester tester, BudgetProgress budget) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: fixedScreens(
          budgets: Reading<MonthBudgets>(
            MonthBudgets(YearMonth(2026, 11), <BudgetProgress>[budget]),
            readAt,
          ),
        ),
        child: const MaterialApp(home: BudgetsScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('las cifras y el porcentaje, los del servidor', (
    WidgetTester tester,
  ) async {
    await pump(tester, food(near: true, exceeded: false));

    expect(find.text('noviembre de 2026'), findsOneWidget);
    expect(
      find.textContaining(r'$ 1.200.000,00 de $ 1.000.000,00 · 87 %'),
      findsOneWidget,
    );
    expect(find.text(r'Queda -$ 200.000,00'), findsOneWidget);
  });

  testWidgets('«cerca del límite» y no «excedido», porque así lo dijo', (
    WidgetTester tester,
  ) async {
    await pump(tester, food(near: true, exceeded: false));

    expect(find.byKey(BudgetTile.nearKey), findsOneWidget);
    expect(find.byKey(BudgetTile.exceededKey), findsNothing);
  });

  testWidgets('«excedido» cuando el servidor lo dice', (
    WidgetTester tester,
  ) async {
    await pump(tester, food(near: true, exceeded: true));

    expect(find.byKey(BudgetTile.exceededKey), findsOneWidget);
  });

  testWidgets('sin ningún aviso si el servidor no da ninguno', (
    WidgetTester tester,
  ) async {
    await pump(tester, food(near: false, exceeded: false));

    expect(find.byKey(BudgetTile.nearKey), findsNothing);
    expect(find.byKey(BudgetTile.exceededKey), findsNothing);
  });
}

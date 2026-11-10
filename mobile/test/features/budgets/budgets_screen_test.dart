import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/ledger/budget.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/core/theme/app_theme.dart';
import 'package:mobile/core/theme/finance_colors.dart';
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
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: const BudgetsScreen(),
        ),
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

  group(
    'el color de la barra sale del aviso del servidor, no del porcentaje',
    () {
      Color? barColor(WidgetTester tester) => tester
          .widget<LinearProgressIndicator>(find.byType(LinearProgressIndicator))
          .color;

      testWidgets('excedido: el color de gasto', (WidgetTester tester) async {
        await pump(tester, food(near: true, exceeded: true));

        expect(barColor(tester), FinanceColors.light.expense);
      });

      testWidgets('cerca del límite: el de aviso', (WidgetTester tester) async {
        await pump(tester, food(near: true, exceeded: false));

        expect(barColor(tester), FinanceColors.light.warning);
      });

      testWidgets('sin aviso, el de la app, aunque el porcentaje sea alto', (
        WidgetTester tester,
      ) async {
        await pump(tester, food(near: false, exceeded: false));

        expect(
          barColor(tester),
          appTheme(Brightness.light).colorScheme.primary,
        );
      });
    },
  );

  testWidgets('sin presupuestos, ofrece poner el primero', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: fixedScreens(),
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: const BudgetsScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Sin presupuestos este mes'), findsOneWidget);
    expect(find.text('Poner un límite'), findsOneWidget);
  });
}

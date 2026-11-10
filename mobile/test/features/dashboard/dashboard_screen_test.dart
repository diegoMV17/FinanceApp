import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/ledger/account_balance.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/month_summary.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/core/theme/app_theme.dart';
import 'package:mobile/core/theme/finance_colors.dart';
import 'package:mobile/core/widgets/reading_notices.dart';
import 'package:mobile/features/dashboard/data/dashboard.dart';
import 'package:mobile/features/dashboard/ui/dashboard_screen.dart';

import '../../support/screen_doubles.dart';

/// El tablero muestra las cifras del servidor, tal cual, y dice siempre
/// cuándo no son de ahora y cuántos pendientes no cuentan todavía.
void main() {
  final MonthSummary november = MonthSummary(
    asOf: CalendarDate(2026, 11, 9),
    monthFrom: CalendarDate(2026, 11, 1),
    moneyReallyMine: Money.ofCents(150000000),
    income: Money.ofCents(500000000),
    expenses: Money.ofCents(1250050),
  );
  final Dashboard dashboard = Dashboard(
    summary: november,
    balances: <AccountBalance>[
      AccountBalance(
        const AccountRef(id: 'a', name: 'Nequi', kind: AccountKind.asset),
        Money.ofCents(-2500),
      ),
      AccountBalance(
        const AccountRef(id: 'e', name: 'Mercado', kind: AccountKind.expense),
        Money.ofCents(99),
      ),
    ],
    overTime: <MonthSummary>[november],
    expensesByCategory: <CategoryTotal>[
      CategoryTotal(
        const AccountRef(
          id: 'e',
          name: 'Alimentación',
          kind: AccountKind.expense,
        ),
        Money.ofCents(1250050),
      ),
    ],
  );

  Future<void> pump(
    WidgetTester tester, {
    RequestFailure? stale,
    int waiting = 0,
  }) async {
    // Alto, para que la lista construya todo sin desplazarse.
    tester.view.physicalSize = const Size(1080, 6000);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: fixedScreens(
          dashboard: Reading<Dashboard>(dashboard, readAt, stale),
          waiting: waiting,
        ),
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: DashboardScreen(onOpenAccounts: () {}),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('las cifras del mes, formateadas desde los centavos', (
    WidgetTester tester,
  ) async {
    await pump(tester);

    expect(
      tester.widget<Text>(find.byKey(DashboardScreen.moneyReallyMineKey)).data,
      r'$ 1.500.000,00',
    );
    expect(find.text(r'$ 5.000.000,00'), findsOneWidget);
    // Gastos del mes y la categoría que los explica: los dos, al centavo.
    expect(find.text(r'$ 12.500,50'), findsNWidgets(2));
    expect(find.text('Alimentación'), findsOneWidget);
  });

  testWidgets('un saldo negativo se ve negativo: el signo es del servidor', (
    WidgetTester tester,
  ) async {
    await pump(tester);

    expect(find.text(r'-$ 25,00'), findsOneWidget);
  });

  testWidgets('las categorías no se listan entre los saldos', (
    WidgetTester tester,
  ) async {
    await pump(tester);

    expect(find.text('Mercado'), findsNothing);
  });

  testWidgets('los pendientes que todavía no cuentan, con su número', (
    WidgetTester tester,
  ) async {
    await pump(tester, waiting: 3);

    expect(find.byKey(DashboardScreen.waitingKey), findsOneWidget);
    expect(find.textContaining('3 movimientos pendientes'), findsOneWidget);
  });

  testWidgets('sin pendientes, no hay aviso', (WidgetTester tester) async {
    await pump(tester);

    expect(find.byKey(DashboardScreen.waitingKey), findsNothing);
  });

  testWidgets('la última copia dice que lo es, de cuándo y por qué', (
    WidgetTester tester,
  ) async {
    await pump(tester, stale: const RequestFailure(FailureKind.noAnswer));

    expect(find.byKey(StaleNotice.noticeKey), findsOneWidget);
    expect(find.textContaining('puede estar desactualizado'), findsOneWidget);
    expect(find.textContaining('Tailscale'), findsOneWidget);
  });

  testWidgets('lo de ahora no lleva aviso', (WidgetTester tester) async {
    await pump(tester);

    expect(find.byKey(StaleNotice.noticeKey), findsNothing);
  });

  testWidgets('ingresos y gastos van en su color por papel; un saldo '
      'negativo, en el del texto', (WidgetTester tester) async {
    await pump(tester);
    final ThemeData theme = Theme.of(
      tester.element(find.byType(DashboardScreen)),
    );
    final FinanceColors colors = theme.extension<FinanceColors>()!;
    Color? colorOf(String text) =>
        tester.widgetList<Text>(find.text(text)).first.style?.color;

    expect(colorOf(r'$ 5.000.000,00'), colors.income);
    expect(colorOf(r'$ 12.500,50'), colors.expense);
    expect(colorOf(r'-$ 25,00'), theme.colorScheme.onSurface);
  });

  testWidgets('el eje de la serie se marca con las cifras del servidor', (
    WidgetTester tester,
  ) async {
    final MonthSummary october = MonthSummary(
      asOf: CalendarDate(2026, 10, 31),
      monthFrom: CalendarDate(2026, 10, 1),
      moneyReallyMine: Money.ofCents(100000000),
      income: Money.zero,
      expenses: Money.zero,
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: fixedScreens(
          dashboard: Reading<Dashboard>(
            Dashboard(
              summary: november,
              balances: dashboard.balances,
              overTime: <MonthSummary>[october, november],
              expensesByCategory: const <CategoryTotal>[],
            ),
            readAt,
          ),
        ),
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: DashboardScreen(onOpenAccounts: () {}),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text(r'$ 1 M'), findsOneWidget);
    expect(find.text(r'$ 1,5 M'), findsOneWidget);
  });

  testWidgets('sin cuentas, ofrece abrir la primera', (
    WidgetTester tester,
  ) async {
    int opened = 0;
    await tester.pumpWidget(
      ProviderScope(
        overrides: fixedScreens(
          dashboard: Reading<Dashboard>(emptyDashboard(), readAt),
        ),
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: DashboardScreen(onOpenAccounts: () => opened++),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Abrir una cuenta'));

    expect(find.text('Todavía no hay cuentas'), findsOneWidget);
    expect(opened, 1);
  });
}

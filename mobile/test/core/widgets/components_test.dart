import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/theme/app_theme.dart';
import 'package:mobile/core/theme/finance_colors.dart';
import 'package:mobile/core/widgets/components.dart';

/// Las piezas que comparten las pantallas. Lo que importa de ellas: la cifra
/// se escribe desde los centavos, con cifras tabulares, y su color sale del
/// papel, nunca del signo.
void main() {
  Future<ThemeData> pump(WidgetTester tester, Widget child) async {
    final ThemeData theme = appTheme(Brightness.light);
    await tester.pumpWidget(
      MaterialApp(
        theme: theme,
        home: Scaffold(body: child),
      ),
    );
    return theme;
  }

  Text textOf(WidgetTester tester, String data) =>
      tester.widget<Text>(find.text(data));

  group('MoneyText', () {
    testWidgets('escribe la cifra desde los centavos, con cifras tabulares', (
      WidgetTester tester,
    ) async {
      await pump(tester, MoneyText(Money.ofCents(1234567)));

      final Text text = textOf(tester, r'$ 12.345,67');
      expect(
        text.style!.fontFeatures,
        contains(const FontFeature.tabularFigures()),
      );
    });

    testWidgets('un negativo neutral va en el color del texto: el signo es '
        'el lado del asiento, no bueno o malo', (WidgetTester tester) async {
      final ThemeData theme = await pump(
        tester,
        MoneyText(Money.ofCents(-2500)),
      );

      expect(
        textOf(tester, r'-$ 25,00').style!.color,
        theme.colorScheme.onSurface,
      );
    });

    testWidgets('un gasto va en el color de gasto, aunque sea positivo', (
      WidgetTester tester,
    ) async {
      final ThemeData theme = await pump(
        tester,
        MoneyText(Money.ofCents(2500), role: MoneyRole.expense),
      );

      expect(
        textOf(tester, r'$ 25,00').style!.color,
        theme.extension<FinanceColors>()!.expense,
      );
    });
  });

  testWidgets('StatTile: la etiqueta y la cifra con su papel', (
    WidgetTester tester,
  ) async {
    final ThemeData theme = await pump(
      tester,
      StatTile(
        label: 'Ingresos del mes',
        amount: Money.ofCents(500000000),
        role: MoneyRole.income,
        icon: Icons.south_west,
      ),
    );

    expect(find.text('Ingresos del mes'), findsOneWidget);
    expect(
      textOf(tester, r'$ 5.000.000,00').style!.color,
      theme.extension<FinanceColors>()!.income,
    );
  });

  testWidgets('EmptyState: dice qué falta y ofrece la acción', (
    WidgetTester tester,
  ) async {
    int taps = 0;
    await pump(
      tester,
      EmptyState(
        icon: Icons.account_balance_wallet_outlined,
        title: 'Todavía no hay cuentas',
        message: 'Abre la primera.',
        actionLabel: 'Abrir cuenta',
        onAction: () => taps++,
      ),
    );

    await tester.tap(find.text('Abrir cuenta'));

    expect(find.text('Todavía no hay cuentas'), findsOneWidget);
    expect(taps, 1);
  });

  testWidgets('SectionCard: título y contenido', (WidgetTester tester) async {
    await pump(
      tester,
      const SectionCard(title: 'Saldos', child: Text('Nequi')),
    );

    expect(find.text('Saldos'), findsOneWidget);
    expect(find.text('Nequi'), findsOneWidget);
  });

  test('las iniciales de un nombre', () {
    expect(initialsOf('Juan Pérez'), 'JP');
    expect(initialsOf('  ana  '), 'A');
    expect(initialsOf('María José de la Torre'), 'MT');
    expect(initialsOf('😀 Feliz'), '😀F');
    expect(initialsOf(''), '?');
  });
}

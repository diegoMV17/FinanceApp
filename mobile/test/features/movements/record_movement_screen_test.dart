import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/clock.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/core/theme/app_theme.dart';
import 'package:mobile/features/movements/ui/record_movement_screen.dart';
import 'package:mobile/features/movements/ui/record_movement_view_model.dart';

import '../../support/screen_doubles.dart';

/// Registrar: cada hueco ofrece sus cuentas como chips, y si falta alguna se
/// dice cuál y se ofrece abrirla. Lo que pasa al enviar está probado en el
/// view model, sin widgets.
void main() {
  const List<AccountRef> accounts = <AccountRef>[
    AccountRef(id: 'a', name: 'Nequi', kind: AccountKind.asset),
    AccountRef(id: 'e', name: 'Mercado', kind: AccountKind.expense),
  ];

  Future<ProviderContainer> pump(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1080, 4800);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final ProviderContainer container = ProviderContainer(
      overrides: [
        clockProvider.overrideWithValue(() => DateTime(2026, 11, 9, 10)),
        recordableAccountsProvider.overrideWith(
          (Ref ref) async => Reading<List<AccountRef>>(accounts, readAt),
        ),
      ],
    );
    addTearDown(container.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: RecordMovementScreen(onOpenAccounts: () {}),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return container;
  }

  testWidgets('un gasto: de una cuenta de plata a una categoría, con chips', (
    WidgetTester tester,
  ) async {
    final ProviderContainer container = await pump(tester);

    await tester.tap(find.widgetWithText(ChoiceChip, 'Nequi'));
    await tester.tap(find.widgetWithText(ChoiceChip, 'Mercado'));
    await tester.pump();

    final RecordMovementState state = container.read(
      recordMovementViewModelProvider,
    );
    expect(state.first, 'a');
    expect(state.second, 'e');
  });

  testWidgets('un ingreso sin categorías de ingreso: dice qué falta', (
    WidgetTester tester,
  ) async {
    await pump(tester);

    await tester.tap(find.widgetWithText(ChoiceChip, 'Ingreso'));
    await tester.pumpAndSettle();

    expect(find.text('Faltan cuentas para esto'), findsOneWidget);
    expect(find.text('Abrir cuentas'), findsOneWidget);
  });

  testWidgets('«Hoy» viene elegido; «Ayer» cambia la fecha', (
    WidgetTester tester,
  ) async {
    final ProviderContainer container = await pump(tester);

    expect(
      tester
          .widget<ChoiceChip>(find.widgetWithText(ChoiceChip, 'Hoy'))
          .selected,
      isTrue,
    );
    await tester.tap(find.widgetWithText(ChoiceChip, 'Ayer'));
    await tester.pump();

    expect(container.read(recordMovementViewModelProvider).date.day, 8);
  });
}

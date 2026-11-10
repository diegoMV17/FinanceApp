import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/ledger/account_balance.dart';
import 'package:mobile/core/ledger/person.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/features/people/ui/people_screen.dart';

import '../../support/screen_doubles.dart';

void main() {
  PersonPosition person(String id, String name, int net) => PersonPosition(
    id: id,
    name: name,
    isArchived: false,
    owedToYou: Money.ofCents(10000000),
    youOwe: Money.ofCents(3000000),
    theirFund: Money.ofCents(5000000),
    netOwedToYou: Money.ofCents(net),
    accounts: const <AccountBalance>[],
  );

  Future<void> pump(WidgetTester tester, List<PersonPosition> people) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: fixedScreens(
          people: Reading<List<PersonPosition>>(people, readAt),
        ),
        child: const MaterialApp(home: PeopleScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('cada persona con su neto, como lo da el servidor', (
    WidgetTester tester,
  ) async {
    await pump(tester, <PersonPosition>[
      person('a', 'Juan', 7000000),
      person('b', 'Ana', -150000),
      person('c', 'Luis', 0),
    ]);

    expect(find.text(r'$ 70.000,00'), findsOneWidget);
    expect(find.text('Te debe, neto'), findsOneWidget);
    expect(find.text(r'-$ 1.500,00'), findsOneWidget);
    expect(find.text('Le debes, neto'), findsOneWidget);
    expect(find.text('A paz y salvo'), findsOneWidget);
  });

  testWidgets('el detalle muestra los tres lados, sin derivarlos', (
    WidgetTester tester,
  ) async {
    await pump(tester, <PersonPosition>[person('a', 'Juan', 7000000)]);

    await tester.tap(find.text('Juan'));
    await tester.pumpAndSettle();

    expect(find.text(r'$ 100.000,00'), findsOneWidget);
    expect(find.text(r'$ 30.000,00'), findsOneWidget);
    expect(find.text(r'$ 50.000,00'), findsOneWidget);
    expect(find.text(r'$ 70.000,00'), findsOneWidget);
  });
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/app.dart';

void main() {
  testWidgets('la app arranca y dice qué es', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: FinanceApp()));

    expect(find.text('FinanceApp'), findsOneWidget);
  });

  testWidgets('el esqueleto no muestra plata ni datos del libro', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const ProviderScope(child: FinanceApp()));

    // Los saldos los decide el servidor y todavía no hay con qué pedirlos:
    // una cifra en esta pantalla solo podría ser inventada.
    expect(find.textContaining(r'$'), findsNothing);
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}

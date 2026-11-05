import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/lock/privacy_cover.dart';

/// En iOS, la foto que el sistema toma para el selector de apps se hace con
/// la app en `inactive`: lo que se vea en ese momento es lo que queda a la
/// vista. En Android lo tapa FLAG_SECURE, desde MainActivity.
void main() {
  Future<void> pumpCovered(WidgetTester tester) => tester.pumpWidget(
    const MaterialApp(home: PrivacyCover(child: Text('Saldo del libro'))),
  );

  testWidgets('con la app al frente se ve lo de siempre', (
    WidgetTester tester,
  ) async {
    await pumpCovered(tester);

    expect(find.text('Saldo del libro'), findsOneWidget);
    expect(find.byKey(PrivacyCover.coverKey), findsNothing);
  });

  for (final AppLifecycleState away in <AppLifecycleState>[
    AppLifecycleState.inactive,
    AppLifecycleState.hidden,
    AppLifecycleState.paused,
  ]) {
    testWidgets('en ${away.name}, la cubierta tapa todo', (
      WidgetTester tester,
    ) async {
      await pumpCovered(tester);

      tester.binding.handleAppLifecycleStateChanged(away);
      await tester.pump();

      expect(find.byKey(PrivacyCover.coverKey), findsOneWidget);
      expect(find.text('Saldo del libro'), findsNothing);
    });
  }

  testWidgets('al volver, la cubierta se va', (WidgetTester tester) async {
    await pumpCovered(tester);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    await tester.pump();

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();

    expect(find.text('Saldo del libro'), findsOneWidget);
  });
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/lock/authenticator.dart';
import 'package:mobile/core/lock/lock_gate.dart';

import 'app_lock_test.dart' show FakeAuthenticator;

void main() {
  late FakeAuthenticator authenticator;

  Future<void> pumpGate(WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [authenticatorProvider.overrideWithValue(authenticator)],
        child: const MaterialApp(home: LockGate(child: Text('Dentro'))),
      ),
    );
    await tester.pumpAndSettle();
  }

  setUp(() => authenticator = FakeAuthenticator());

  testWidgets('pide el desbloqueo solo, al abrir, y si pasa deja entrar', (
    WidgetTester tester,
  ) async {
    authenticator.next = AuthOutcome.success;

    await pumpGate(tester);

    expect(authenticator.calls, 1);
    expect(find.text('Dentro'), findsOneWidget);
  });

  testWidgets('si no pasa, se queda en la pantalla de bloqueo', (
    WidgetTester tester,
  ) async {
    authenticator.next = AuthOutcome.failed;

    await pumpGate(tester);

    expect(find.text('Dentro'), findsNothing);
    expect(find.byKey(LockGate.unlockButtonKey), findsOneWidget);

    authenticator.next = AuthOutcome.success;
    await tester.tap(find.byKey(LockGate.unlockButtonKey));
    await tester.pumpAndSettle();

    expect(find.text('Dentro'), findsOneWidget);
  });

  testWidgets('al irse y volver, hay que desbloquear otra vez', (
    WidgetTester tester,
  ) async {
    authenticator.next = AuthOutcome.success;
    await pumpGate(tester);

    // En segundo plano no se dibuja nada (lo que se ve en el selector de apps
    // lo tapan PrivacyCover y FLAG_SECURE); lo que importa es que al volver
    // no se entre sin desbloquear. Esta vez el usuario cancela.
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    authenticator.next = AuthOutcome.failed;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();

    expect(authenticator.calls, 2);
    expect(find.text('Dentro'), findsNothing);
    expect(find.byKey(LockGate.unlockButtonKey), findsOneWidget);
  });

  testWidgets('una pantalla abierta encima también queda tapada al bloquear', (
    WidgetTester tester,
  ) async {
    // Como en la app: la compuerta por encima del Navigator. Si fuera la
    // pantalla de inicio, una ruta empujada encima seguiría a la vista.
    authenticator.next = AuthOutcome.success;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [authenticatorProvider.overrideWithValue(authenticator)],
        child: MaterialApp(
          builder: (BuildContext context, Widget? child) =>
              LockGate(child: child!),
          home: Builder(
            builder: (BuildContext context) => TextButton(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const Scaffold(body: Text('Saldo de Juan')),
                ),
              ),
              child: const Text('Abrir'),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Abrir'));
    await tester.pumpAndSettle();
    expect(find.text('Saldo de Juan'), findsOneWidget);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    authenticator.next = AuthOutcome.failed;
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();

    expect(find.text('Saldo de Juan'), findsNothing);
    expect(find.byKey(LockGate.unlockButtonKey), findsOneWidget);

    // Y al desbloquear, la pantalla sigue donde estaba.
    authenticator.next = AuthOutcome.success;
    await tester.tap(find.byKey(LockGate.unlockButtonKey));
    await tester.pumpAndSettle();
    expect(find.text('Saldo de Juan'), findsOneWidget);
  });

  testWidgets('sin bloqueo de pantalla en el teléfono: entra, con el aviso', (
    WidgetTester tester,
  ) async {
    authenticator.next = AuthOutcome.noDeviceLock;

    await pumpGate(tester);

    expect(find.text('Dentro'), findsOneWidget);
    expect(find.byKey(LockGate.unprotectedNoticeKey), findsOneWidget);
  });
}

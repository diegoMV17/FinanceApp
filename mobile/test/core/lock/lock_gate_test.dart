import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/lock/authenticator.dart';
import 'package:mobile/core/lock/lock_gate.dart';
import 'package:mobile/core/lock/privacy_cover.dart';

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

  testWidgets(
    'bloqueada, un campo de un formulario de debajo pierde el foco: el '
    'teclado no escribe en lo que no se ve',
    (WidgetTester tester) async {
      authenticator.next = AuthOutcome.success;
      final FocusNode field = FocusNode();
      addTearDown(field.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [authenticatorProvider.overrideWithValue(authenticator)],
          child: MaterialApp(
            home: LockGate(
              child: Material(child: TextField(focusNode: field)),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      field.requestFocus();
      await tester.pump();
      expect(field.hasFocus, isTrue);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      await tester.pump();

      expect(field.hasFocus, isFalse);
    },
  );

  group('cancelar no vuelve a pedir', () {
    // Visto en un teléfono: el diálogo del sistema le quita el foco a la app, y
    // al cerrarse se lo devuelve con un `resumed`. Si ese `resumed` pidiera el
    // desbloqueo, cancelar abriría otro diálogo y nunca se llegaría a la
    // pantalla de bloqueo.
    testWidgets('el diálogo se cierra y la app recupera el foco', (
      WidgetTester tester,
    ) async {
      authenticator.next = AuthOutcome.failed;
      await pumpGate(tester);
      expect(authenticator.calls, 1);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      expect(authenticator.calls, 1);
      expect(find.byKey(LockGate.unlockButtonKey), findsOneWidget);
    });

    testWidgets('el PIN abrió otra actividad y la app pasó a segundo plano', (
      WidgetTester tester,
    ) async {
      final Completer<AuthOutcome> pending = Completer<AuthOutcome>();
      authenticator.pending = pending;
      await pumpGate(tester);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      pending.complete(AuthOutcome.failed);
      await tester.pump();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      expect(authenticator.calls, 1);
      expect(find.byKey(LockGate.unlockButtonKey), findsOneWidget);
    });

    testWidgets('pero irse de verdad y volver sí pide, una sola vez', (
      WidgetTester tester,
    ) async {
      authenticator.next = AuthOutcome.failed;
      await pumpGate(tester);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(authenticator.calls, 2);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(authenticator.calls, 2);
      expect(find.byKey(LockGate.unlockButtonKey), findsOneWidget);
    });
  });

  group('con la cubierta encima, como en la app', () {
    // Visto en un teléfono. Entre `inactive` y `paused` pasan cuadros: si la
    // cubierta sacara al bloqueo del árbol, este dejaría de escuchar y la app
    // se quedaría abierta al irse; y al volver se crearía de nuevo, pidiendo
    // otra vez. Por eso cada `inactive` aquí va seguido de un cuadro.
    Future<void> pumpCovered(WidgetTester tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [authenticatorProvider.overrideWithValue(authenticator)],
          child: MaterialApp(
            builder: (BuildContext context, Widget? child) =>
                PrivacyCover(child: LockGate(child: child!)),
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
    }

    Future<void> lifecycle(WidgetTester tester, AppLifecycleState state) async {
      tester.binding.handleAppLifecycleStateChanged(state);
      await tester.pump();
    }

    testWidgets('irse de verdad la cierra, y al volver pide desbloquear', (
      WidgetTester tester,
    ) async {
      authenticator.next = AuthOutcome.success;
      await pumpCovered(tester);
      expect(authenticator.calls, 1);

      await lifecycle(tester, AppLifecycleState.inactive);
      await lifecycle(tester, AppLifecycleState.hidden);
      await lifecycle(tester, AppLifecycleState.paused);
      authenticator.next = AuthOutcome.failed;
      await lifecycle(tester, AppLifecycleState.hidden);
      await lifecycle(tester, AppLifecycleState.inactive);
      await lifecycle(tester, AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      expect(authenticator.calls, 2);
      expect(find.text('Abrir'), findsNothing);
      expect(find.byKey(LockGate.unlockButtonKey), findsOneWidget);
    });

    testWidgets('cancelar el diálogo no abre otro', (
      WidgetTester tester,
    ) async {
      authenticator.next = AuthOutcome.failed;
      await pumpCovered(tester);

      await lifecycle(tester, AppLifecycleState.inactive);
      await lifecycle(tester, AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      expect(authenticator.calls, 1);
      expect(find.byKey(LockGate.unlockButtonKey), findsOneWidget);
    });

    testWidgets('perder el foco un momento no cierra las pantallas abiertas', (
      WidgetTester tester,
    ) async {
      authenticator.next = AuthOutcome.success;
      await pumpCovered(tester);
      await tester.tap(find.text('Abrir'));
      await tester.pumpAndSettle();

      await lifecycle(tester, AppLifecycleState.inactive);
      expect(find.text('Saldo de Juan'), findsNothing);
      await lifecycle(tester, AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      expect(find.text('Saldo de Juan'), findsOneWidget);
      expect(authenticator.calls, 1);
    });
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

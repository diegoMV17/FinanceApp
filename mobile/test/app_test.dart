import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/app.dart';
import 'package:mobile/core/lock/authenticator.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/connection_check.dart';
import 'package:mobile/core/storage/local_database.dart';
import 'package:mobile/core/storage/secure_store.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/core/storage/server_settings_store.dart';
import 'package:mobile/features/settings/ui/connection_status_view.dart';
import 'package:mobile/features/settings/ui/server_setup_screen.dart';

import 'core/lock/app_lock_test.dart' show FakeAuthenticator;
import 'support/fake_secure_store.dart';
import 'support/recording_adapter.dart';

/// La app entera, de la compuerta hacia dentro, sin teléfono: almacenamiento
/// seguro en memoria, desbloqueo falso, la base en memoria y un transporte
/// falso donde estaría el servidor.
void main() {
  const String address = 'https://finanzas.ejemplo.invalid';
  const String token =
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';

  late FakeSecureStore secure;
  late FakeAuthenticator authenticator;
  late RecordingAdapter adapter;
  Object? databaseFailure;

  setUp(() {
    secure = FakeSecureStore();
    authenticator = FakeAuthenticator()..next = AuthOutcome.success;
    adapter = RecordingAdapter();
    databaseFailure = null;
  });

  Future<void> pumpApp(WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          secureStoreProvider.overrideWithValue(secure),
          authenticatorProvider.overrideWithValue(authenticator),
          connectionCheckProvider.overrideWithValue(
            ConnectionCheck(
              (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
            ),
          ),
          localDatabaseProvider.overrideWith((Ref ref) async {
            final Object? failure = databaseFailure;
            if (failure != null) {
              throw failure;
            }
            final LocalDatabase db = LocalDatabase(NativeDatabase.memory());
            ref.onDispose(db.close);
            return db;
          }),
        ],
        child: const FinanceApp(),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> saveSettings() => ServerSettingsStore(secure).save(
    ServerSettings(
      address: ServerAddress.parse(address),
      token: ApiToken(token),
    ),
  );

  testWidgets('la primera vez pide la dirección y el token', (
    WidgetTester tester,
  ) async {
    await pumpApp(tester);

    expect(find.byType(ServerSetupScreen), findsOneWidget);
  });

  testWidgets('sin desbloquear no se ve ni la pantalla de conexión', (
    WidgetTester tester,
  ) async {
    authenticator.next = AuthOutcome.failed;

    await pumpApp(tester);

    expect(find.byType(ServerSetupScreen), findsNothing);
    expect(find.text('Desbloquear'), findsOneWidget);
  });

  testWidgets('el campo del token va oculto y sin sugerencias del teclado', (
    WidgetTester tester,
  ) async {
    await pumpApp(tester);

    final TextField field = tester.widget<TextField>(
      find.byKey(ServerSetupScreen.tokenFieldKey),
    );
    expect(field.obscureText, isTrue);
    expect(field.enableSuggestions, isFalse);
    expect(field.autocorrect, isFalse);
    expect(field.enableIMEPersonalizedLearning, isFalse);
  });

  testWidgets('con el 200 del servidor entra, y el token no aparece', (
    WidgetTester tester,
  ) async {
    adapter.answer(200, <String, Object?>{
      'asOf': '2026-11-02',
      'balances': <Object?>[],
    });
    await pumpApp(tester);

    await tester.enterText(
      find.byKey(ServerSetupScreen.addressFieldKey),
      address,
    );
    await tester.enterText(find.byKey(ServerSetupScreen.tokenFieldKey), token);
    await tester.tap(find.byKey(ServerSetupScreen.connectButtonKey));
    await tester.pumpAndSettle();

    expect(find.byType(ServerSetupScreen), findsNothing);
    expect(find.textContaining('finanzas.ejemplo.invalid'), findsOneWidget);
    expect(find.textContaining(token), findsNothing);
  });

  testWidgets('con un 401 se queda, dice por qué y no guarda nada', (
    WidgetTester tester,
  ) async {
    adapter.answer(401, <String, Object?>{});
    await pumpApp(tester);

    await tester.enterText(
      find.byKey(ServerSetupScreen.addressFieldKey),
      address,
    );
    await tester.enterText(find.byKey(ServerSetupScreen.tokenFieldKey), token);
    await tester.tap(find.byKey(ServerSetupScreen.connectButtonKey));
    await tester.pumpAndSettle();

    expect(find.byType(ServerSetupScreen), findsOneWidget);
    expect(find.textContaining('token'), findsWidgets);
    expect(secure.values, isEmpty);
  });

  testWidgets('ya configurada, entra directo y no muestra cifras inventadas', (
    WidgetTester tester,
  ) async {
    await saveSettings();

    await pumpApp(tester);

    expect(find.byType(ServerSetupScreen), findsNothing);
    expect(find.textContaining('finanzas.ejemplo.invalid'), findsOneWidget);
    // Los saldos los decide el servidor y llegan con las pantallas (7d): una
    // cifra aquí solo podría ser inventada.
    expect(find.textContaining(r'$'), findsNothing);
  });

  group('adentro, la conexión con el servidor', () {
    void answerLikeTheApi() => adapter.answer(200, <String, Object?>{
      'asOf': '2026-11-02',
      'balances': <Object?>[],
    });

    testWidgets('dice «conectada» solo porque el servidor respondió', (
      WidgetTester tester,
    ) async {
      await saveSettings();
      answerLikeTheApi();

      await pumpApp(tester);

      expect(adapter.requests, hasLength(1));
      expect(adapter.last.uri.path, '/api/balances');
      expect(find.textContaining('Conectada a'), findsOneWidget);
      expect(find.textContaining(token), findsNothing);
    });

    testWidgets(
      'sin llegar al servidor: lo dice, no se cae y conserva la configuración',
      (WidgetTester tester) async {
        await saveSettings();
        final Map<String, String> before = Map<String, String>.of(
          secure.values,
        );
        adapter.fail(const SocketException('sin red'));

        await pumpApp(tester);

        expect(find.textContaining('Conectada a'), findsNothing);
        expect(find.textContaining('Tailscale'), findsOneWidget);
        expect(find.textContaining('finanzas.ejemplo.invalid'), findsOneWidget);
        expect(find.byType(ServerSetupScreen), findsNothing);
        expect(secure.values, before);
      },
    );

    testWidgets('reintentar vuelve a probar, y al responder dice conectada', (
      WidgetTester tester,
    ) async {
      await saveSettings();
      adapter.fail(const SocketException('sin red'));
      await pumpApp(tester);

      answerLikeTheApi();
      await tester.tap(find.byKey(ConnectionStatusView.retryButtonKey));
      await tester.pumpAndSettle();

      expect(adapter.requests, hasLength(2));
      expect(find.textContaining('Conectada a'), findsOneWidget);
      expect(find.byKey(ConnectionStatusView.retryButtonKey), findsNothing);
    });

    testWidgets(
      'un token que el servidor ya no acepta se dice, y no se borra',
      (WidgetTester tester) async {
        await saveSettings();
        adapter.answer(401, <String, Object?>{});

        await pumpApp(tester);

        expect(find.textContaining('Conectada a'), findsNothing);
        expect(find.textContaining('token'), findsOneWidget);
        expect(secure.values[ServerSettingsStore.tokenKey], token);
      },
    );

    testWidgets('bloqueada, el token no sale a la red', (
      WidgetTester tester,
    ) async {
      await saveSettings();
      answerLikeTheApi();
      authenticator.next = AuthOutcome.failed;

      await pumpApp(tester);
      expect(adapter.requests, isEmpty);

      authenticator.next = AuthOutcome.success;
      await tester.tap(find.text('Desbloquear'));
      await tester.pumpAndSettle();
      expect(adapter.requests, hasLength(1));
    });

    testWidgets('cada vuelta de segundo plano la comprueba de nuevo', (
      WidgetTester tester,
    ) async {
      await saveSettings();
      answerLikeTheApi();
      await pumpApp(tester);
      expect(find.textContaining('Conectada a'), findsOneWidget);

      // Se fue a apagar Tailscale. «Conectada» ya no sería verdad.
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      adapter.fail(const SocketException('sin red'));
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();

      expect(adapter.requests, hasLength(2));
      expect(find.textContaining('Conectada a'), findsNothing);
      expect(find.textContaining('Tailscale'), findsOneWidget);
    });
  });

  testWidgets('sin el motor que cifra, la app no continúa', (
    WidgetTester tester,
  ) async {
    await saveSettings();
    databaseFailure = const EncryptionUnavailable();

    await pumpApp(tester);

    expect(find.byKey(FinanceApp.encryptionUnavailableKey), findsOneWidget);
    expect(find.textContaining('finanzas.ejemplo.invalid'), findsNothing);
    expect(find.byType(ServerSetupScreen), findsNothing);
  });

  testWidgets('una base que no se puede leer: lo dice y ofrece borrarla', (
    WidgetTester tester,
  ) async {
    await saveSettings();
    databaseFailure = const LocalDatabaseUnreadable();

    await pumpApp(tester);

    expect(find.byKey(FinanceApp.unreadableDatabaseKey), findsOneWidget);
    expect(find.byKey(FinanceApp.eraseDatabaseButtonKey), findsOneWidget);
    expect(find.textContaining('finanzas.ejemplo.invalid'), findsNothing);
  });

  testWidgets('borrar la base pide confirmación antes de hacer nada', (
    WidgetTester tester,
  ) async {
    await saveSettings();
    databaseFailure = const LocalDatabaseUnreadable();
    await pumpApp(tester);

    await tester.tap(find.byKey(FinanceApp.eraseDatabaseButtonKey));
    await tester.pumpAndSettle();

    expect(find.byType(AlertDialog), findsOneWidget);
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(find.byKey(FinanceApp.unreadableDatabaseKey), findsOneWidget);
  });
}

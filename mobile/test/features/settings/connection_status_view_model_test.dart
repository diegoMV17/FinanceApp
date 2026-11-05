import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/lock/app_lock.dart';
import 'package:mobile/core/lock/authenticator.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/connection_check.dart';
import 'package:mobile/core/storage/secure_store.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/core/storage/server_settings_store.dart';
import 'package:mobile/features/settings/ui/connection_status_view_model.dart';

import '../../core/lock/app_lock_test.dart' show FakeAuthenticator;
import '../../support/fake_secure_store.dart';
import '../../support/recording_adapter.dart';

/// Sin widgets a propósito: en la app, el bloqueo también apaga `TickerMode` y
/// Riverpod pausa lo que hay debajo, así que un test de widget no ve si esta
/// regla existe. Que el token no salga con la app bloqueada no puede depender
/// de eso.
void main() {
  late FakeSecureStore secure;
  late FakeAuthenticator authenticator;
  late RecordingAdapter adapter;
  late ProviderContainer container;

  Future<ConnectionResult?> status() =>
      container.read(connectionStatusProvider.future);

  setUp(() async {
    secure = FakeSecureStore();
    authenticator = FakeAuthenticator()..next = AuthOutcome.success;
    adapter = RecordingAdapter()
      ..answer(200, <String, Object?>{
        'asOf': '2026-11-02',
        'balances': <Object?>[],
      });
    container = ProviderContainer(
      overrides: [
        secureStoreProvider.overrideWithValue(secure),
        authenticatorProvider.overrideWithValue(authenticator),
        connectionCheckProvider.overrideWithValue(
          ConnectionCheck(
            (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
          ),
        ),
      ],
    )..listen(connectionStatusProvider, (_, _) {});
    await ServerSettingsStore(secure).save(
      ServerSettings(
        address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
        token: ApiToken('0123456789abcdef' * 4),
      ),
    );
  });

  tearDown(() => container.dispose());

  test('bloqueada, no prueba nada: el token no sale a la red', () async {
    expect(await status(), isNull);
    expect(adapter.requests, isEmpty);
  });

  test('al desbloquear, prueba una vez', () async {
    await container.read(appLockProvider.notifier).unlock();

    expect(await status(), ConnectionResult.connected);
    expect(adapter.requests, hasLength(1));
  });

  test('al volver a bloquearse, no vuelve a salir', () async {
    await container.read(appLockProvider.notifier).unlock();
    await status();

    container
        .read(appLockProvider.notifier)
        .onLifecycle(AppLifecycleState.paused);

    expect(await status(), isNull);
    expect(adapter.requests, hasLength(1));
  });
}

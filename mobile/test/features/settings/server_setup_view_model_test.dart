import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/connection_check.dart';
import 'package:mobile/core/storage/secure_store.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/core/storage/server_settings_store.dart';
import 'package:mobile/features/settings/ui/server_setup_view_model.dart';

import '../../support/fake_secure_store.dart';
import '../../support/recording_adapter.dart';

void main() {
  const String address = 'https://finanzas.ejemplo.invalid';
  const String token =
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';

  late FakeSecureStore secure;
  late RecordingAdapter adapter;
  late ProviderContainer container;

  ServerSetupViewModel model() =>
      container.read(serverSetupViewModelProvider.notifier);
  ServerSetupState state() => container.read(serverSetupViewModelProvider);

  setUp(() {
    secure = FakeSecureStore();
    adapter = RecordingAdapter();
    container = ProviderContainer(
      overrides: [
        secureStoreProvider.overrideWithValue(secure),
        connectionCheckProvider.overrideWithValue(
          ConnectionCheck(
            (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
          ),
        ),
      ],
    );
  });

  tearDown(() => container.dispose());

  void answerLikeTheApi() => adapter.answer(200, <String, Object?>{
    'asOf': '2026-11-02',
    'accounts': <Object?>[],
  });

  test(
    'una dirección o un token mal formados ni siquiera salen a la red',
    () async {
      await model().submit('http://finanzas.ejemplo.invalid', 'corto');

      expect(state().addressError, isNotNull);
      expect(state().tokenError, isNotNull);
      expect(adapter.requests, isEmpty);
      expect(secure.values, isEmpty);
    },
  );

  test('si el servidor responde con el token, se guarda', () async {
    answerLikeTheApi();

    await model().submit(address, token);

    expect(state().connected, isTrue);
    expect(
      await container.read(serverSettingsStoreProvider).read(),
      isA<ServerSettings>().having(
        (ServerSettings s) => s.address.origin,
        'origin',
        address,
      ),
    );
  });

  test('y la app se entera: la configuración vigente cambia', () async {
    expect(await container.read(serverSettingsProvider.future), isNull);
    answerLikeTheApi();

    await model().submit(address, token);

    expect(await container.read(serverSettingsProvider.future), isNotNull);
  });

  final Map<int, ConnectionResult> refused = <int, ConnectionResult>{
    401: ConnectionResult.wrongToken,
    429: ConnectionResult.tooManyAttempts,
    503: ConnectionResult.unexpected,
  };
  for (final MapEntry<int, ConnectionResult> entry in refused.entries) {
    test('un ${entry.key} no guarda nada y dice por qué', () async {
      adapter.answer(entry.key, <String, Object?>{});

      await model().submit(address, token);

      expect(state().connected, isFalse);
      expect(state().failure, entry.value);
      expect(secure.values, isEmpty);
    });
  }

  test('ningún mensaje repite el token', () async {
    adapter.answer(401, <String, Object?>{});

    await model().submit(address, token);

    expect(state().toString(), isNot(contains(token)));
    expect(state().failureMessage, isNot(contains(token)));
  });
}

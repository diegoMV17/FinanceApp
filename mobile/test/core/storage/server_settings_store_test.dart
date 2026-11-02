import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/core/storage/server_settings_store.dart';

import '../../support/fake_secure_store.dart';

void main() {
  const String token =
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
  final ServerSettings settings = ServerSettings(
    address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
    token: ApiToken(token),
  );

  late FakeSecureStore secure;
  late ServerSettingsStore store;

  setUp(() {
    secure = FakeSecureStore();
    store = ServerSettingsStore(secure);
  });

  test('sin nada guardado no hay configuración', () async {
    expect(await store.read(), isNull);
  });

  test('lo guardado se lee igual', () async {
    await store.save(settings);

    final ServerSettings? read = await store.read();

    expect(read!.address.origin, 'https://finanzas.ejemplo.invalid');
    expect(read.token.value, token);
  });

  test('la dirección y el token van al almacenamiento seguro', () async {
    await store.save(settings);

    expect(secure.values[ServerSettingsStore.tokenKey], token);
    expect(
      secure.values[ServerSettingsStore.addressKey],
      'https://finanzas.ejemplo.invalid',
    );
  });

  test('borrar deja la app sin configuración', () async {
    await store.save(settings);

    await store.clear();

    expect(await store.read(), isNull);
    expect(secure.values, isEmpty);
  });

  test('una mitad sola no es una configuración', () async {
    await store.save(settings);
    secure.values.remove(ServerSettingsStore.tokenKey);

    expect(await store.read(), isNull);
  });

  test('lo guardado que ya no es válido no se usa', () async {
    secure.values[ServerSettingsStore.addressKey] = 'http://en-claro.invalid';
    secure.values[ServerSettingsStore.tokenKey] = token;

    expect(await store.read(), isNull);
  });
}

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:mobile/core/lock/app_lock.dart';
import 'package:mobile/core/lock/authenticator.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/server_gateway.dart';
import 'package:mobile/core/storage/secure_store.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/data/local/local_database.dart';

import '../core/lock/app_lock_test.dart' show FakeAuthenticator;
import 'fake_secure_store.dart';
import 'server_fixture.dart';

/// Los proveedores de la app de verdad sobre el servidor falso y la base en
/// memoria de [server]: el bloqueo es el de la app, así que la puerta no deja
/// salir nada hasta [unlock].
ProviderContainer appContainer(
  ServerFixture server, {
  List<Override> overrides = const <Override>[],
}) {
  late final ProviderContainer container;
  container = ProviderContainer(
    overrides: <Override>[
      secureStoreProvider.overrideWithValue(FakeSecureStore()),
      authenticatorProvider.overrideWithValue(
        FakeAuthenticator()..next = AuthOutcome.success,
      ),
      localDatabaseProvider.overrideWith((Ref ref) async => server.db),
      serverGatewayProvider.overrideWith(
        (Ref ref) async => ServerGateway(
          ServerFixture.settings,
          () => container.read(appLockProvider).isOpen,
          (ServerSettings s) => dioFor(s)..httpClientAdapter = server.adapter,
        ),
      ),
      ...overrides,
    ],
  );
  return container;
}

Future<void> unlock(ProviderContainer container) =>
    container.read(appLockProvider.notifier).unlock();

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/connection_check.dart';
import '../../../core/storage/server_settings.dart';
import '../../../core/storage/server_settings_store.dart';

/// La conexión con el servidor, vista desde la pantalla que la configura.
///
/// Guarda solo después de una prueba que respondió: así nunca queda guardado
/// un par de dirección y token que no funciona.
class SettingsRepository {
  const SettingsRepository(this._store, this._check);

  final ServerSettingsStore _store;
  final ConnectionCheck _check;

  Future<ConnectionResult> connect(ServerSettings settings) async {
    final ConnectionResult result = await _check.run(settings);
    if (result == ConnectionResult.connected) {
      await _store.save(settings);
    }
    return result;
  }
}

final Provider<SettingsRepository> settingsRepositoryProvider =
    Provider<SettingsRepository>(
      (Ref ref) => SettingsRepository(
        ref.watch(serverSettingsStoreProvider),
        ref.watch(connectionCheckProvider),
      ),
    );

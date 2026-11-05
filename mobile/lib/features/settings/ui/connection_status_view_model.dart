import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/lock/app_lock.dart';
import '../../../core/network/connection_check.dart';
import '../../../core/storage/server_settings.dart';
import '../../../core/storage/server_settings_store.dart';
import '../data/settings_repository.dart';

/// Si el servidor configurado responde, comprobado y no supuesto.
///
/// `null` mientras no hay nada que decir. Depende del bloqueo por dos razones:
/// bloqueada, el token no sale a la red (la pantalla de adentro existe, fuera de
/// escena, también antes de desbloquear); y cada desbloqueo vuelve a comprobar,
/// porque lo que pasó mientras la app no estaba al frente —apagar Tailscale,
/// cambiar de red— es justo lo que deja de ser verdad.
class ConnectionStatusViewModel extends AsyncNotifier<ConnectionResult?> {
  @override
  Future<ConnectionResult?> build() async {
    final bool open = ref.watch(
      appLockProvider.select(
        (AppLockState state) =>
            state == AppLockState.unlocked || state == AppLockState.unprotected,
      ),
    );
    if (!open) {
      return null;
    }
    final ServerSettings? settings = await ref.watch(
      serverSettingsProvider.future,
    );
    if (settings == null) {
      return null;
    }
    return ref.read(settingsRepositoryProvider).check(settings);
  }

  void retry() => ref.invalidateSelf();
}

final AsyncNotifierProvider<ConnectionStatusViewModel, ConnectionResult?>
connectionStatusProvider =
    AsyncNotifierProvider<ConnectionStatusViewModel, ConnectionResult?>(
      ConnectionStatusViewModel.new,
    );

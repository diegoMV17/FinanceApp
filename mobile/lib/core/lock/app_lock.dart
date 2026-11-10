import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'authenticator.dart';

enum AppLockState {
  locked,

  /// El diálogo del sistema está a la vista.
  unlocking,
  unlocked,

  /// El teléfono no tiene bloqueo de pantalla: se deja entrar con un aviso
  /// permanente, y se vuelve a preguntar cada vez que la app vuelve.
  unprotected;

  /// Se puede mostrar lo de adentro y usar el token.
  bool get isOpen => this == unlocked || this == unprotected;
}

/// El bloqueo de la app: cerrada al abrir y cada vez que vuelve de segundo
/// plano, sin periodo de gracia.
///
/// Se cierra con `hidden` y `paused`, que es irse de verdad; no con
/// `inactive`, que también es lo que pasa cuando el sistema muestra su propio
/// diálogo de huella encima. Y nada la cierra mientras se está desbloqueando:
/// en Android, pedir el PIN abre otra actividad y la app pasa a segundo plano,
/// así que cerrarla ahí haría que el desbloqueo no terminara nunca.
class AppLock extends Notifier<AppLockState> {
  Future<void>? _unlocking;
  bool _wentAway = false;

  @override
  AppLockState build() => AppLockState.locked;

  /// Pide el desbloqueo al sistema. Si ya hay un diálogo en curso, espera ese
  /// en vez de abrir otro.
  Future<void> unlock() {
    if (state == AppLockState.unlocked) {
      return Future<void>.value();
    }
    return _unlocking ??= _ask().whenComplete(() => _unlocking = null);
  }

  Future<void> _ask() async {
    state = AppLockState.unlocking;
    final AuthOutcome outcome = await ref
        .read(authenticatorProvider)
        .authenticate();
    state = switch (outcome) {
      AuthOutcome.success => AppLockState.unlocked,
      AuthOutcome.failed => AppLockState.locked,
      AuthOutcome.noDeviceLock => AppLockState.unprotected,
    };
  }

  /// Al volver, pide el desbloqueo solo si la app se había ido de verdad. Un
  /// `resumed` a secas no basta: es también lo que llega cuando se cierra el
  /// diálogo del sistema, y pedir ahí haría que cancelar abriera otro diálogo,
  /// sin llegar nunca a la pantalla de bloqueo. Lo que pase mientras se
  /// desbloquea es del diálogo y no cuenta como irse.
  void onLifecycle(AppLifecycleState lifecycle) {
    switch (lifecycle) {
      case AppLifecycleState.hidden || AppLifecycleState.paused:
        if (state != AppLockState.unlocking) {
          state = AppLockState.locked;
          _wentAway = true;
        }
      case AppLifecycleState.resumed:
        if (_wentAway) {
          _wentAway = false;
          unlock();
        }
      case AppLifecycleState.inactive || AppLifecycleState.detached:
        break;
    }
  }
}

final NotifierProvider<AppLock, AppLockState> appLockProvider =
    NotifierProvider<AppLock, AppLockState>(AppLock.new);

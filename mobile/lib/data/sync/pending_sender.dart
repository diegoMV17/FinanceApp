import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/lock/app_lock.dart';
import '../../core/storage/server_settings.dart';
import '../../core/storage/server_settings_store.dart';
import '../api/export.dart';
import '../local/pending_movements_store.dart';
import 'movement_transport.dart';
import 'pending_movement.dart';
import 'send_verdict.dart';

/// El temporizador de los reintentos, con su reloj: en los tests corre cuando
/// el test lo dice.
abstract interface class SendTimer {
  DateTime get now;

  /// Reemplaza lo que hubiera programado.
  void schedule(Duration after, Future<void> Function() callback);

  void cancel();
}

class SystemSendTimer implements SendTimer {
  Timer? _timer;

  @override
  DateTime get now => DateTime.now();

  @override
  void schedule(Duration after, Future<void> Function() callback) {
    _timer?.cancel();
    _timer = Timer(after, () => unawaited(callback()));
  }

  @override
  void cancel() => _timer?.cancel();
}

final Provider<SendTimer> sendTimerProvider = Provider<SendTimer>(
  (Ref ref) => SystemSendTimer(),
);

/// Envía la cola. Su estado es por qué está en pausa, o `null` si no lo está.
///
/// - **Bloqueada, nada sale:** el token no viaja sin haber desbloqueado. Se
///   comprueba antes de cada envío, y no depende de ninguna pantalla.
/// - **Una pasada a la vez,** en orden de llegada. Lo que se pida mientras tanto
///   corre al terminar.
/// - Solo con la app al frente: al desbloquear, al encolar y al vencer una
///   espera. En segundo plano la app queda bloqueada, y por eso mismo no envía.
/// - **`Retry-After` se respeta** aunque se pida enviar antes o la app se
///   bloquee y vuelva. La espera creciente, en cambio, sí se salta si se pide:
///   si la red volvió, no hay por qué hacer esperar al usuario.
class PendingSender extends Notifier<PauseReason?> {
  late SendTimer _timer;
  Future<void>? _running;
  bool _wakeAgain = false;
  int _failures = 0;
  DateTime? _serverSaidWaitUntil;

  @override
  PauseReason? build() {
    final SendTimer timer = ref.watch(sendTimerProvider);
    _timer = timer;
    ref
      ..listen<bool>(
        appLockProvider.select((AppLockState state) => state.isOpen),
        (_, bool open) {
          if (open) {
            unawaited(wake());
          } else {
            timer.cancel();
          }
        },
      )
      ..onDispose(timer.cancel);
    return null;
  }

  /// Pide una pasada; el futuro termina cuando no queda ninguna en curso.
  Future<void> wake() {
    final Future<void>? running = _running;
    if (running != null) {
      _wakeAgain = true;
      return running;
    }
    return _running = _passes().whenComplete(() => _running = null);
  }

  /// Una pausa termina el bucle aunque se haya pedido otra pasada: insistir de
  /// inmediato gastaría otro intento, y con un token malo sumaría otro fallo en
  /// el servidor. Lo pedido sale cuando venza la espera.
  Future<void> _passes() async {
    bool paused;
    do {
      _wakeAgain = false;
      paused = await _pass();
    } while (_wakeAgain && !paused && ref.mounted);
  }

  bool get _mayTalk => ref.mounted && ref.read(appLockProvider).isOpen;

  /// Dice si terminó en pausa.
  Future<bool> _pass() async {
    if (!_mayTalk || _stillWaitingForTheServer()) {
      return false;
    }
    _timer.cancel();
    final PendingMovementsStore store;
    final ServerSettings? settings;
    try {
      store = await ref.read(pendingMovementsStoreProvider.future);
      settings = await ref.read(serverSettingsProvider.future);
    } on Object {
      // La base no abrió o no se pudo leer la configuración: la compuerta de la
      // app ya lo está diciendo, y aquí no hay nada que enviar.
      return false;
    }
    if (settings == null) {
      return false;
    }
    for (final PendingMovement movement in await store.waiting()) {
      if (!_mayTalk) {
        return false;
      }
      final SendVerdict verdict = await _send(settings, movement);
      if (!ref.mounted) {
        return false;
      }
      switch (verdict) {
        case Accepted():
          await store.remove(movement.id);
        case Conflict(:final String problemType):
          await store.markConflict(movement.id, problemType);
        case Rejected(:final String problemType):
          await store.markRejected(movement.id, problemType);
        case Paused():
          _pauseFor(verdict);
          return true;
      }
    }
    _failures = 0;
    state = null;
    return false;
  }

  Future<SendVerdict> _send(
    ServerSettings settings,
    PendingMovement movement,
  ) async {
    try {
      final MovementResponse response = await ref
          .read(movementTransportProvider)
          .send(settings, movement.request);
      return verdictForResponse(movement.id, response);
    } on Object catch (failure) {
      return verdictForFailure(failure);
    }
  }

  void _pauseFor(Paused verdict) {
    state = verdict.reason;
    if (verdict.reason == PauseReason.unauthorized) {
      // Insistir con un token malo no lo arregla y suma fallos en el servidor.
      // Se vuelve a probar al desbloquear o al registrar otro movimiento.
      return;
    }
    _failures++;
    final Duration? asked = verdict.retryAfter;
    if (asked != null) {
      _serverSaidWaitUntil = _timer.now.add(asked);
    }
    _timer.schedule(asked ?? backoffAfter(_failures), wake);
  }

  /// Si sigue vigente lo que pidió el servidor, deja el reintento para entonces.
  bool _stillWaitingForTheServer() {
    final DateTime? until = _serverSaidWaitUntil;
    if (until == null) {
      return false;
    }
    final Duration left = until.difference(_timer.now);
    if (left > Duration.zero) {
      _timer.schedule(left, wake);
      return true;
    }
    _serverSaidWaitUntil = null;
    return false;
  }
}

final NotifierProvider<PendingSender, PauseReason?> pendingSenderProvider =
    NotifierProvider<PendingSender, PauseReason?>(PendingSender.new);

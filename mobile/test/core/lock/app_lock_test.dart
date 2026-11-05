import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/lock/app_lock.dart';
import 'package:mobile/core/lock/authenticator.dart';

void main() {
  late FakeAuthenticator authenticator;
  late ProviderContainer container;

  AppLock lock() => container.read(appLockProvider.notifier);
  AppLockState state() => container.read(appLockProvider);

  setUp(() {
    authenticator = FakeAuthenticator();
    container = ProviderContainer(
      overrides: [authenticatorProvider.overrideWithValue(authenticator)],
    );
  });

  tearDown(() => container.dispose());

  test('arranca bloqueada', () {
    expect(state(), AppLockState.locked);
  });

  test('un desbloqueo correcto la abre', () async {
    authenticator.next = AuthOutcome.success;

    await lock().unlock();

    expect(state(), AppLockState.unlocked);
  });

  test('un desbloqueo fallido o cancelado la deja cerrada', () async {
    authenticator.next = AuthOutcome.failed;

    await lock().unlock();

    expect(state(), AppLockState.locked);
  });

  test(
    'irse a segundo plano la vuelve a cerrar, sin periodo de gracia',
    () async {
      authenticator.next = AuthOutcome.success;
      await lock().unlock();

      lock().onLifecycle(AppLifecycleState.hidden);

      expect(state(), AppLockState.locked);
    },
  );

  test('que salga el diálogo del sistema (inactive) no la cierra', () async {
    authenticator.next = AuthOutcome.success;
    await lock().unlock();

    lock().onLifecycle(AppLifecycleState.inactive);

    expect(state(), AppLockState.unlocked);
  });

  test(
    'mientras se desbloquea, el diálogo del PIN no la vuelve a cerrar',
    () async {
      // En Android, pedir el PIN abre otra actividad y la app pasa a segundo
      // plano: si eso cerrara la app, el desbloqueo nunca terminaría.
      final Completer<AuthOutcome> pending = Completer<AuthOutcome>();
      authenticator.pending = pending;

      final Future<void> unlocking = lock().unlock();
      expect(state(), AppLockState.unlocking);
      lock().onLifecycle(AppLifecycleState.paused);
      pending.complete(AuthOutcome.success);
      await unlocking;

      expect(state(), AppLockState.unlocked);
    },
  );

  test('pedir desbloquear dos veces a la vez abre un solo diálogo', () async {
    final Completer<AuthOutcome> pending = Completer<AuthOutcome>();
    authenticator.pending = pending;

    final Future<void> first = lock().unlock();
    final Future<void> second = lock().unlock();
    pending.complete(AuthOutcome.success);
    await Future.wait(<Future<void>>[first, second]);

    expect(authenticator.calls, 1);
  });

  group('un teléfono sin bloqueo de pantalla', () {
    test('deja entrar, marcado como sin protección', () async {
      authenticator.next = AuthOutcome.noDeviceLock;

      await lock().unlock();

      expect(state(), AppLockState.unprotected);
    });

    test('y al volver se vuelve a preguntar: quizá ya puso un PIN', () async {
      authenticator.next = AuthOutcome.noDeviceLock;
      await lock().unlock();

      lock().onLifecycle(AppLifecycleState.paused);
      authenticator.next = AuthOutcome.success;
      await lock().unlock();

      expect(state(), AppLockState.unlocked);
      expect(authenticator.calls, 2);
    });
  });
}

class FakeAuthenticator implements Authenticator {
  AuthOutcome next = AuthOutcome.failed;
  Completer<AuthOutcome>? pending;
  int calls = 0;

  @override
  Future<AuthOutcome> authenticate() {
    calls++;
    final Completer<AuthOutcome>? waiting = pending;
    pending = null;
    return waiting?.future ?? Future<AuthOutcome>.value(next);
  }
}

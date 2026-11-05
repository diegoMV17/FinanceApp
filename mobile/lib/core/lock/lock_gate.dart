import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_lock.dart';

/// Nada de lo que hay debajo se ve sin desbloquear.
///
/// Va en `MaterialApp.builder`, por encima del Navigator y no como pantalla de
/// inicio: así también tapa cualquier pantalla que se haya abierto encima con
/// `Navigator.push`, que como `home` quedaría a la vista al bloquear.
///
/// Pide el desbloqueo solo al abrir y cada vez que la app vuelve al frente; si
/// el usuario lo cancela, queda la pantalla de bloqueo con un botón para
/// intentarlo otra vez. En un teléfono sin bloqueo de pantalla deja entrar,
/// con un aviso permanente arriba.
class LockGate extends ConsumerStatefulWidget {
  const LockGate({required this.child, super.key});

  static const Key unlockButtonKey = Key('unlock');
  static const Key unprotectedNoticeKey = Key('unprotected-notice');

  final Widget child;

  @override
  ConsumerState<LockGate> createState() => _LockGateState();
}

class _LockGateState extends ConsumerState<LockGate>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => _unlock());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final AppLock lock = ref.read(appLockProvider.notifier)..onLifecycle(state);
    if (state == AppLifecycleState.resumed &&
        ref.read(appLockProvider) == AppLockState.locked) {
      lock.unlock();
    }
  }

  void _unlock() {
    if (mounted) {
      ref.read(appLockProvider.notifier).unlock();
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppLockState state = ref.watch(appLockProvider);
    final bool open =
        state == AppLockState.unlocked || state == AppLockState.unprotected;
    // La misma forma de árbol abierta o cerrada: lo de debajo (el Navigator
    // entero, con las pantallas que haya abiertas) se conserva mientras está
    // bloqueada, pero fuera de escena: ni se dibuja, ni recibe toques, ni lo
    // ve la accesibilidad. Al desbloquear, cada pantalla sigue donde estaba.
    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        Offstage(
          offstage: !open,
          child: TickerMode(
            enabled: open,
            child: Column(
              children: <Widget>[
                if (state == AppLockState.unprotected)
                  const _UnprotectedNotice(key: LockGate.unprotectedNoticeKey),
                Expanded(child: widget.child),
              ],
            ),
          ),
        ),
        if (!open) _LockScreen(onUnlock: _unlock),
      ],
    );
  }
}

class _LockScreen extends StatelessWidget {
  const _LockScreen({required this.onUnlock});

  final VoidCallback onUnlock;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const Icon(Icons.lock_outline, size: 48),
              const SizedBox(height: 16),
              Text(
                'FinanceApp está bloqueada',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 24),
              FilledButton(
                key: LockGate.unlockButtonKey,
                onPressed: onUnlock,
                child: const Text('Desbloquear'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _UnprotectedNotice extends StatelessWidget {
  const _UnprotectedNotice({super.key});

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Material(
      color: colors.errorContainer,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Text(
            'Este teléfono no tiene bloqueo de pantalla: cualquiera que lo '
            'tome puede abrir la app. Configura un PIN o una huella en los '
            'ajustes del sistema.',
            style: TextStyle(color: colors.onErrorContainer),
          ),
        ),
      ),
    );
  }
}

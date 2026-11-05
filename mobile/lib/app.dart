import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/lock/lock_gate.dart';
import 'core/lock/privacy_cover.dart';
import 'core/storage/local_database.dart';
import 'core/storage/server_settings.dart';
import 'core/storage/server_settings_store.dart';
import 'data/local/local_database.dart';
import 'features/settings/ui/connection_status_view.dart';
import 'features/settings/ui/server_setup_screen.dart';

/// Arranque, tema y la compuerta de la app.
///
/// De fuera hacia dentro: la cubierta que tapa la app cuando no está al
/// frente; el bloqueo con huella o PIN; la base local, que tiene que abrir
/// cifrada o la app no continúa; y la configuración: sin dirección y token,
/// la pantalla de conexión.
class FinanceApp extends StatelessWidget {
  const FinanceApp({super.key});

  static const Key encryptionUnavailableKey = Key('encryption-unavailable');
  static const Key unreadableDatabaseKey = Key('unreadable-database');
  static const Key eraseDatabaseButtonKey = Key('erase-database');

  static const Color _seed = Color(0xFF1B6B50);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FinanceApp',
      theme: ThemeData(colorSchemeSeed: _seed, brightness: Brightness.light),
      darkTheme: ThemeData(colorSchemeSeed: _seed, brightness: Brightness.dark),
      // La cubierta y el bloqueo, por encima del Navigator: tapan también
      // cualquier pantalla o diálogo que se haya abierto encima.
      builder: (BuildContext context, Widget? child) => PrivacyCover(
        child: LockGate(child: child ?? const SizedBox.shrink()),
      ),
      home: const _DatabaseGate(child: _SettingsGate()),
    );
  }
}

/// La base local tiene que abrir, y cifrada.
class _DatabaseGate extends ConsumerWidget {
  const _DatabaseGate({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ref
        .watch(localDatabaseProvider)
        .when(
          data: (_) => child,
          loading: () => const _Waiting(),
          error: (Object failure, _) => switch (failure) {
            LocalDatabaseUnreadable() => const _UnreadableDatabase(),
            // Sin cifrado, o cualquier otra cosa que impida abrirla: la app no
            // continúa. Nunca una base en claro "mientras tanto".
            _ => const _Stop(
              key: FinanceApp.encryptionUnavailableKey,
              message:
                  'Esta instalación no puede cifrar la base local, y la app no '
                  'continúa sin cifrado. Reinstálala desde una versión '
                  'oficial.',
            ),
          },
        );
  }
}

/// Sin dirección y token, la pantalla de conexión; con ellos, adentro.
class _SettingsGate extends ConsumerWidget {
  const _SettingsGate();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ref
        .watch(serverSettingsProvider)
        .when(
          data: (ServerSettings? settings) => settings == null
              ? const ServerSetupScreen()
              : _HomeScreen(settings: settings),
          loading: () => const _Waiting(),
          error: (Object failure, _) => const ServerSetupScreen(),
        );
  }
}

/// Adentro. Todavía sin datos: las pantallas llegan en el 7d. No pinta
/// ninguna cifra, porque los saldos los decide el servidor y una cifra aquí
/// solo podría ser inventada. Dice si el servidor responde; el token, nunca.
class _HomeScreen extends StatelessWidget {
  const _HomeScreen({required this.settings});

  final ServerSettings settings;

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                Text('FinanceApp', style: text.headlineMedium),
                const SizedBox(height: 8),
                ConnectionStatusView(origin: settings.address.origin),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _UnreadableDatabase extends ConsumerWidget {
  const _UnreadableDatabase();

  Future<void> _erase(BuildContext context, WidgetRef ref) async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('¿Borrar la base local?'),
        content: const Text(
          'Se pierde lo que no se haya enviado al servidor. Lo que ya está en '
          'el servidor no se toca.',
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Borrar'),
          ),
        ],
      ),
    );
    if (confirmed ?? false) {
      await ref.read(localDatabaseOpenerProvider).erase();
      ref.invalidate(localDatabaseProvider);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _Stop(
      key: FinanceApp.unreadableDatabaseKey,
      message:
          'La base local de este teléfono no se puede leer: su clave se '
          'perdió. Se puede borrar y empezar de nuevo desde el servidor.',
      action: FilledButton(
        key: FinanceApp.eraseDatabaseButtonKey,
        onPressed: () => _erase(context, ref),
        child: const Text('Borrar la base local'),
      ),
    );
  }
}

class _Stop extends StatelessWidget {
  const _Stop({required this.message, this.action, super.key});

  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final Widget? extra = action;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                const Icon(Icons.error_outline, size: 48),
                const SizedBox(height: 16),
                Text(message, textAlign: TextAlign.center),
                if (extra != null) ...<Widget>[
                  const SizedBox(height: 24),
                  extra,
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Waiting extends StatelessWidget {
  const _Waiting();

  @override
  Widget build(BuildContext context) =>
      const Scaffold(body: Center(child: CircularProgressIndicator()));
}

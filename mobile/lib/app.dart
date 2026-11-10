import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/lock/lock_gate.dart';
import 'core/lock/privacy_cover.dart';
import 'core/storage/local_database.dart';
import 'core/storage/server_settings.dart';
import 'core/storage/server_settings_store.dart';
import 'core/theme/app_theme.dart';
import 'data/local/local_database.dart';
import 'data/sync/pending_sender.dart';
import 'features/settings/ui/server_setup_screen.dart';
import 'home.dart';

/// Arranque, tema y la compuerta de la app.
///
/// De fuera hacia dentro: la cubierta que tapa la app cuando no está al
/// frente; el bloqueo con huella o PIN; la base local, que tiene que abrir
/// cifrada o la app no continúa; y la configuración: sin dirección y token,
/// la pantalla de conexión.
class FinanceApp extends ConsumerWidget {
  const FinanceApp({super.key});

  static const Key encryptionUnavailableKey = Key('encryption-unavailable');
  static const Key unreadableDatabaseKey = Key('unreadable-database');
  static const Key eraseDatabaseButtonKey = Key('erase-database');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // El envío de la cola se escucha aquí, en la raíz, y no en una pantalla: lo
    // que queda debajo del bloqueo lo pausa Riverpod, y el envío tiene que oír
    // justo cuándo se desbloquea. Que bloqueada no salga nada lo decide él.
    ref.listen(pendingSenderProvider, (_, _) {});
    return MaterialApp(
      title: 'FinanceApp',
      theme: appTheme(Brightness.light),
      darkTheme: appTheme(Brightness.dark),
      // En español de Colombia: el selector de fecha, los textos de Material
      // y la semana empezando en lunes.
      locale: const Locale('es', 'CO'),
      supportedLocales: const <Locale>[Locale('es', 'CO'), Locale('es')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
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
              : HomeShell(settings: settings),
          loading: () => const _Waiting(),
          error: (Object failure, _) => const ServerSetupScreen(),
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

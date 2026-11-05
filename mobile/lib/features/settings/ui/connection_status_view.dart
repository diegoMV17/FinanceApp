import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/connection_check.dart';
import 'connection_messages.dart';
import 'connection_status_view_model.dart';

/// A qué servidor está conectada la app, solo si respondió; si no, por qué no,
/// con la opción de reintentar. Nunca muestra el token.
class ConnectionStatusView extends ConsumerWidget {
  const ConnectionStatusView({required this.origin, super.key});

  static const Key retryButtonKey = Key('retry-connection');

  final String origin;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ref
        .watch(connectionStatusProvider)
        .when(
          // Reintentar muestra que está probando, no el error anterior.
          skipLoadingOnRefresh: false,
          data: (ConnectionResult? result) => switch (result) {
            null => _Checking(origin: origin),
            ConnectionResult.connected => Text(
              'Conectada a $origin',
              textAlign: TextAlign.center,
            ),
            _ => _failed(ref, result),
          },
          loading: () => _Checking(origin: origin),
          error: (Object failure, _) =>
              _failed(ref, ConnectionResult.unexpected),
        );
  }

  Widget _failed(WidgetRef ref, ConnectionResult result) => Column(
    mainAxisSize: MainAxisSize.min,
    children: <Widget>[
      Text('Sin conexión con $origin', textAlign: TextAlign.center),
      const SizedBox(height: 8),
      Text(result.message ?? '', textAlign: TextAlign.center),
      const SizedBox(height: 16),
      FilledButton(
        key: retryButtonKey,
        onPressed: ref.read(connectionStatusProvider.notifier).retry,
        child: const Text('Reintentar'),
      ),
    ],
  );
}

class _Checking extends StatelessWidget {
  const _Checking({required this.origin});

  final String origin;

  @override
  Widget build(BuildContext context) =>
      Text('Comprobando la conexión con $origin…', textAlign: TextAlign.center);
}

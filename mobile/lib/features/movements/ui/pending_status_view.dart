import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/pending_movements_repository.dart';

/// Los pendientes se ven siempre, con su número: si algo no llegó al servidor,
/// el usuario lo tiene que saber.
class PendingStatusView extends ConsumerWidget {
  const PendingStatusView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ref
        .watch(pendingSummaryProvider)
        .when(
          data: _summary,
          loading: () => const SizedBox.shrink(),
          // Si la base no abrió, la compuerta de la app ya lo está diciendo.
          error: (Object failure, _) => const SizedBox.shrink(),
        );
  }

  Widget _summary(PendingSummary summary) {
    final int waiting = summary.waiting;
    final int attention = summary.needingAttention;
    final SendingProblem? problem = summary.problem;
    if (waiting == 0 && attention == 0) {
      return const Text('Sin pendientes', textAlign: TextAlign.center);
    }
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (waiting > 0)
          Text(
            waiting == 1
                ? '1 pendiente de enviar'
                : '$waiting pendientes de enviar',
            textAlign: TextAlign.center,
          ),
        if (attention > 0)
          Text(
            attention == 1
                ? '1 necesita atención'
                : '$attention necesitan atención',
            textAlign: TextAlign.center,
          ),
        if (waiting > 0 && problem != null) ...<Widget>[
          const SizedBox(height: 8),
          Text(_messageFor(problem), textAlign: TextAlign.center),
        ],
      ],
    );
  }

  static String _messageFor(SendingProblem problem) => switch (problem) {
    SendingProblem.token =>
      'El servidor no aceptó el token. Los pendientes siguen guardados.',
    SendingProblem.tooManyAttempts =>
      'Demasiados intentos fallidos desde esta red. Los pendientes siguen '
          'guardados y se reintentará solo.',
    SendingProblem.serverBusy =>
      'El servidor está ocupado. Los pendientes siguen guardados y se '
          'reintentará solo.',
    SendingProblem.noConnection =>
      'Sin conexión con el servidor. Los pendientes siguen guardados y se '
          'reintentará solo.',
    SendingProblem.unexpected =>
      'El servidor respondió algo inesperado. Los pendientes siguen guardados '
          'y se reintentará solo.',
  };
}

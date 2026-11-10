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
          data: (PendingSummary summary) => _summary(context, summary),
          loading: () => const SizedBox.shrink(),
          // Si la base no abrió, la compuerta de la app ya lo está diciendo.
          error: (Object failure, _) => const SizedBox.shrink(),
        );
  }

  /// Discreta, pero siempre a la vista y con su número: sin pendientes es una
  /// píldora tenue; con pendientes, una que se nota; lo que necesita
  /// atención, una en el color de error.
  Widget _summary(BuildContext context, PendingSummary summary) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final int waiting = summary.waiting;
    final int attention = summary.needingAttention;
    final SendingProblem? problem = summary.problem;
    if (waiting == 0 && attention == 0) {
      return Center(
        child: _Pill(
          icon: Icons.cloud_done_outlined,
          text: 'Sin pendientes',
          background: colors.surfaceContainerHigh,
          foreground: colors.onSurfaceVariant,
        ),
      );
    }
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Wrap(
          alignment: WrapAlignment.center,
          spacing: 8,
          runSpacing: 6,
          children: <Widget>[
            if (waiting > 0)
              _Pill(
                icon: Icons.schedule_send_outlined,
                text: waiting == 1
                    ? '1 pendiente de enviar'
                    : '$waiting pendientes de enviar',
                background: colors.secondaryContainer,
                foreground: colors.onSecondaryContainer,
              ),
            if (attention > 0)
              _Pill(
                icon: Icons.error_outline,
                text: attention == 1
                    ? '1 necesita atención'
                    : '$attention necesitan atención',
                background: colors.errorContainer,
                foreground: colors.onErrorContainer,
              ),
          ],
        ),
        if (waiting > 0 && problem != null) ...<Widget>[
          const SizedBox(height: 6),
          Text(
            _messageFor(problem),
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall
                ?.copyWith(color: colors.onSurfaceVariant),
          ),
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

class _Pill extends StatelessWidget {
  const _Pill({
    required this.icon,
    required this.text,
    required this.background,
    required this.foreground,
  });

  final IconData icon;
  final String text;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
    decoration: BoxDecoration(
      color: background,
      borderRadius: BorderRadius.circular(999),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Icon(icon, size: 16, color: foreground),
        const SizedBox(width: 6),
        Text(
          text,
          style: Theme.of(context).textTheme.labelMedium
              ?.copyWith(color: foreground, fontWeight: FontWeight.w600),
        ),
      ],
    ),
  );
}

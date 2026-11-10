import 'package:flutter/material.dart';

import '../dates/time_label.dart';
import '../errors/request_failure.dart';
import '../reading/reading.dart';

/// El aviso de que lo que se ve es la última copia y no lo de ahora: de
/// cuándo es y por qué no hay algo más nuevo. Si la lectura es de ahora, no
/// ocupa lugar.
class StaleNotice extends StatelessWidget {
  const StaleNotice({required this.reading, this.now, super.key});

  static const Key noticeKey = Key('stale-notice');

  final Reading<Object?> reading;

  /// Solo para tests.
  final DateTime? now;

  @override
  Widget build(BuildContext context) {
    final RequestFailure? failure = reading.staleBecause;
    if (failure == null) {
      return const SizedBox.shrink();
    }
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Card(
      key: noticeKey,
      color: colors.tertiaryContainer,
      child: ListTile(
        leading: Icon(Icons.cloud_off, color: colors.onTertiaryContainer),
        title: Text(
          'Mostrando lo leído '
          '${readAtLabel(reading.readAt, now ?? DateTime.now())}: '
          'puede estar desactualizado.',
        ),
        subtitle: Text(failure.message),
      ),
    );
  }
}

/// Lo que se ve cuando no hay ni servidor ni copia: el porqué y cómo
/// reintentar.
class FailureNotice extends StatelessWidget {
  const FailureNotice({
    required this.failure,
    required this.onRetry,
    super.key,
  });

  static const Key retryKey = Key('failure-retry');

  final Object failure;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final Object current = failure;
    // Nunca el texto de una excepción: puede traer la petición entera.
    final String message = current is RequestFailure
        ? current.message
        : 'No se pudo cargar. Intenta de nuevo.';
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.tonal(
              key: retryKey,
              onPressed: onRetry,
              child: const Text('Reintentar'),
            ),
          ],
        ),
      ),
    );
  }
}

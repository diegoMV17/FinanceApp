import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../data/local/pending_movements_store.dart';
import '../../../data/sync/pending_sender.dart';
import '../../../data/sync/send_verdict.dart';

/// Por qué los pendientes no están saliendo, dicho para la interfaz.
enum SendingProblem {
  token,
  tooManyAttempts,
  serverBusy,
  noConnection,
  unexpected,
}

/// Lo que se muestra siempre: cuántos hay y, si no salen, por qué.
class PendingSummary {
  const PendingSummary(this.waiting, this.needingAttention, this.problem);

  /// Por enviar o por reintentar.
  final int waiting;

  /// El servidor los rechazó: los tiene que resolver el usuario.
  final int needingAttention;

  final SendingProblem? problem;
}

final StreamProvider<PendingSummary> pendingSummaryProvider =
    StreamProvider<PendingSummary>((Ref ref) async* {
      final SendingProblem? problem = _problemFor(
        ref.watch(pendingSenderProvider),
      );
      final PendingMovementsStore store = await ref.watch(
        pendingMovementsStoreProvider.future,
      );
      yield* store.counts.map(
        (PendingCounts counts) =>
            PendingSummary(counts.waiting, counts.needingAttention, problem),
      );
    });

SendingProblem? _problemFor(PauseReason? reason) => switch (reason) {
  null => null,
  PauseReason.unauthorized => SendingProblem.token,
  PauseReason.tooManyAttempts => SendingProblem.tooManyAttempts,
  PauseReason.busy => SendingProblem.serverBusy,
  PauseReason.noAnswer => SendingProblem.noConnection,
  PauseReason.unexpected => SendingProblem.unexpected,
};

import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/export.dart';
import '../local/pending_movements_store.dart';
import 'pending_movement.dart';
import 'pending_sender.dart';

/// La entrada a la cola. Todo lo que el usuario registra pasa por aquí.
class PendingQueue {
  const PendingQueue(this._store, this._wake);

  final PendingMovementsStore _store;
  final Future<void> Function() _wake;

  /// Primero se guarda y después se intenta enviar: pase lo que pase con la
  /// red, el movimiento ya no se pierde.
  Future<void> record(MovementRequest movement) async {
    await _store.enqueue(PendingMovement.of(movement));
    unawaited(_wake());
  }
}

final FutureProvider<PendingQueue> pendingQueueProvider =
    FutureProvider<PendingQueue>(
      (Ref ref) async => PendingQueue(
        await ref.watch(pendingMovementsStoreProvider.future),
        ref.read(pendingSenderProvider.notifier).wake,
      ),
    );

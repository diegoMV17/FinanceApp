import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/movement_draft.dart';
import '../../../core/reading/reading.dart';
import '../../../data/catalog/account_catalog.dart';
import '../../../data/local/pending_movements_store.dart';
import '../../../data/sync/movement_request_of.dart';
import '../../../data/sync/pending_queue.dart';

/// El movimiento no quedó guardado en el teléfono: no está registrado y no se
/// va a enviar. La pantalla tiene que decirlo.
class MovementNotRecorded implements Exception {
  const MovementNotRecorded();

  @override
  String toString() => 'MovementNotRecorded';
}

/// Registrar un movimiento: entra a la cola local y de ahí sale cuando se
/// pueda. Registrado aquí no es todavía aplicado en el servidor: hasta que
/// la cola lo envíe, no cuenta en ninguna cifra del tablero.
class MovementsRepository {
  const MovementsRepository(this._queue, this._catalog);

  final PendingQueue _queue;
  final AccountCatalog _catalog;

  /// Lanza [MovementNotRecorded] si no se pudo guardar en la cola.
  Future<void> record(MovementDraft draft) async {
    try {
      await _queue.record(movementRequestOf(draft));
    } on PendingMovementNotSaved {
      throw const MovementNotRecorded();
    }
  }

  /// Las cuentas abiertas, para elegir de dónde sale y a dónde va.
  Future<Reading<List<AccountRef>>> openAccounts() async {
    final Reading<List<AccountRef>> all = await _catalog.accounts();
    return Reading<List<AccountRef>>(
      all.value.where((AccountRef a) => !a.isArchived).toList(),
      all.readAt,
      all.staleBecause,
    );
  }
}

final FutureProvider<MovementsRepository> movementsRepositoryProvider =
    FutureProvider<MovementsRepository>(
      (Ref ref) async => MovementsRepository(
        await ref.watch(pendingQueueProvider.future),
        await ref.watch(accountCatalogProvider.future),
      ),
    );

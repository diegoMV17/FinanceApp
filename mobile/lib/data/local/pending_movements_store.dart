import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../sync/movement_id.dart';
import '../sync/pending_movement.dart';
import 'local_database.dart';
import 'pending_movements.dart';

/// Cuántos hay de cada cosa, para mostrarlo siempre en la interfaz.
class PendingCounts {
  const PendingCounts(this.waiting, this.needingAttention);

  /// Por enviar o por reintentar.
  final int waiting;

  /// El servidor los rechazó: los tiene que resolver el usuario.
  final int needingAttention;

  @override
  bool operator ==(Object other) =>
      other is PendingCounts &&
      other.waiting == waiting &&
      other.needingAttention == needingAttention;

  @override
  int get hashCode => Object.hash(waiting, needingAttention);

  @override
  String toString() => 'PendingCounts($waiting, $needingAttention)';
}

/// Se quiso encolar otro contenido con un id que ya está en la cola. Es un error
/// de programación: el id se genera una vez por movimiento.
class ConflictingPendingMovement implements Exception {
  const ConflictingPendingMovement();

  @override
  String toString() => 'ConflictingPendingMovement';
}

/// No se pudo guardar en la cola. Sin detalles a propósito: el error de SQLite
/// imprime los parámetros de la sentencia, y entre ellos está el cuerpo, con
/// montos y descripciones.
class PendingMovementNotSaved implements Exception {
  const PendingMovementNotSaved();

  @override
  String toString() => 'PendingMovementNotSaved';
}

/// La cola en la base local. Solo guarda y lee: lo que significa cada respuesta
/// del servidor se decide en `data/sync`.
///
/// No hay ningún método que cambie el cuerpo de lo encolado.
class PendingMovementsStore {
  const PendingMovementsStore(this._db);

  final LocalDatabase _db;

  $PendingMovementsTable get _table => _db.pendingMovements;

  /// Lo mismo dos veces es un doble toque, y queda uno.
  Future<void> enqueue(PendingMovement movement) async {
    try {
      await _db.transaction(() async {
        final PendingMovementRow? existing = await _row(movement.id);
        if (existing != null) {
          if (existing.body != movement.body) {
            throw const ConflictingPendingMovement();
          }
          return;
        }
        await _db
            .into(_table)
            .insert(
              PendingMovementsCompanion.insert(
                id: movement.id.value,
                body: movement.body,
                status: PendingStatus.waiting,
              ),
            );
      });
    } on ConflictingPendingMovement {
      rethrow;
    } on Object {
      throw const PendingMovementNotSaved();
    }
  }

  /// Lo que hay que enviar, en orden de llegada.
  ///
  /// Una fila que ya no se lee como un movimiento se aparta como rechazada: no
  /// se puede enviar, no debe frenar a las demás y tampoco se borra sola.
  Future<List<PendingMovement>> waiting() async {
    final List<PendingMovementRow> rows =
        await (_db.select(_table)
              ..where(
                ($PendingMovementsTable row) =>
                    row.status.equalsValue(PendingStatus.waiting),
              )
              ..orderBy(<OrderClauseGenerator<$PendingMovementsTable>>[
                ($PendingMovementsTable row) => OrderingTerm.asc(row.position),
              ]))
            .get();
    final List<PendingMovement> readable = <PendingMovement>[];
    for (final PendingMovementRow row in rows) {
      try {
        readable.add(PendingMovement.restored(row.id, row.body));
      } on FormatException {
        await _setStatus(row.id, PendingStatus.rejected, null);
      }
    }
    return readable;
  }

  /// El servidor lo aceptó.
  Future<void> remove(MovementId id) => (_db.delete(
    _table,
  )..where(($PendingMovementsTable row) => row.id.equals(id.value))).go();

  Future<void> markConflict(MovementId id, String? problemType) =>
      _setStatus(id.value, PendingStatus.conflict, problemType);

  Future<void> markRejected(MovementId id, String? problemType) =>
      _setStatus(id.value, PendingStatus.rejected, problemType);

  /// Quita lo que el servidor rechazó, cuando el usuario lo decide. Lo que
  /// todavía espera su envío no se descarta: se perdería un gasto que el usuario
  /// cree registrado. Dice si había algo que quitar.
  Future<bool> discard(MovementId id) async {
    final int removed =
        await (_db.delete(_table)..where(
              ($PendingMovementsTable row) =>
                  row.id.equals(id.value) &
                  row.status.equalsValue(PendingStatus.waiting).not(),
            ))
            .go();
    return removed > 0;
  }

  /// El conteo, y cada vez que cambia.
  Stream<PendingCounts> get counts => _db
      .customSelect(
        'SELECT '
        "COALESCE(SUM(status = 'waiting'), 0) AS waiting, "
        "COALESCE(SUM(status <> 'waiting'), 0) AS attention "
        'FROM pending_movements',
        readsFrom: <ResultSetImplementation<dynamic, dynamic>>{_table},
      )
      .watchSingle()
      .map(
        (QueryRow row) =>
            PendingCounts(row.read<int>('waiting'), row.read<int>('attention')),
      );

  Future<PendingMovementRow?> _row(MovementId id) =>
      (_db.select(_table)
            ..where(($PendingMovementsTable row) => row.id.equals(id.value)))
          .getSingleOrNull();

  Future<void> _setStatus(
    String id,
    PendingStatus status,
    String? problemType,
  ) =>
      (_db.update(
        _table,
      )..where(($PendingMovementsTable row) => row.id.equals(id))).write(
        PendingMovementsCompanion(
          status: Value<PendingStatus>(status),
          problemType: Value<String?>(problemType),
        ),
      );
}

final FutureProvider<PendingMovementsStore> pendingMovementsStoreProvider =
    FutureProvider<PendingMovementsStore>(
      (Ref ref) async =>
          PendingMovementsStore(await ref.watch(localDatabaseProvider.future)),
    );

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'local_database.dart';

/// Una lectura guardada: la respuesta tal como llegó y cuándo.
class ReadSnapshot {
  const ReadSnapshot(this.body, this.readAt);

  final String body;
  final DateTime readAt;
}

/// No se pudo guardar la copia. Sin detalles: el error de SQLite imprime los
/// parámetros de la sentencia, y entre ellos está el cuerpo, con montos y
/// nombres.
class SnapshotNotSaved implements Exception {
  const SnapshotNotSaved();

  @override
  String toString() => 'SnapshotNotSaved';
}

/// La última copia de cada lectura. Solo guarda y lee: qué clave lleva cada
/// lectura y cómo se interpreta la decide el repositorio que la hizo.
///
/// No tiene cómo editar una copia: solo la reemplaza una lectura más nueva.
class ReadSnapshotsStore {
  const ReadSnapshotsStore(this._db);

  final LocalDatabase _db;

  $ReadSnapshotsTable get _table => _db.readSnapshots;

  /// Guarda [body] como la copia de [key], salvo que ya haya una leída
  /// después: entonces la que llega tarde no cuenta.
  Future<void> save(String key, String body, DateTime readAt) async {
    try {
      await _db.transaction(() async {
        final ReadSnapshotRow? existing =
            await (_db.select(_table)
                  ..where(($ReadSnapshotsTable t) => t.key.equals(key)))
                .getSingleOrNull();
        if (existing != null && !readAt.isAfter(existing.readAt)) {
          return;
        }
        await _db
            .into(_table)
            .insertOnConflictUpdate(
              ReadSnapshotsCompanion.insert(
                key: key,
                body: body,
                readAt: readAt,
              ),
            );
      });
    } on Object {
      throw const SnapshotNotSaved();
    }
  }

  Future<ReadSnapshot?> read(String key) async {
    final ReadSnapshotRow? row = await (_db.select(
      _table,
    )..where(($ReadSnapshotsTable t) => t.key.equals(key))).getSingleOrNull();
    // Drift la devuelve en la hora local; es el mismo instante, en UTC.
    return row == null ? null : ReadSnapshot(row.body, row.readAt.toUtc());
  }
}

final FutureProvider<ReadSnapshotsStore> readSnapshotsStoreProvider =
    FutureProvider<ReadSnapshotsStore>(
      (Ref ref) async =>
          ReadSnapshotsStore(await ref.watch(localDatabaseProvider.future)),
    );

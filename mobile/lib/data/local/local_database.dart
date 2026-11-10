import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/storage/local_database.dart';
import 'pending_movements.dart';
import 'read_snapshots.dart';

part 'local_database.g.dart';

/// La base local de la app, cifrada. La abre [LocalDatabaseOpener].
///
/// Versión 3: la última copia de lo leído (7d). La 2 (la cola, 7c) y la 1 (sin
/// tablas, 7b) están en los teléfonos: se migran, no se recrean.
@DriftDatabase(tables: <Type>[PendingMovements, ReadSnapshots])
class LocalDatabase extends _$LocalDatabase {
  LocalDatabase(super.executor);

  @override
  int get schemaVersion => 3;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (Migrator migrator) async {
      await migrator.createAll();
      await customStatement(pendingMovementsKeepTheirContent);
      await customStatement(readSnapshotsOnlyGetNewer);
    },
    onUpgrade: (Migrator migrator, int from, int to) async {
      if (from < 2) {
        await migrator.createTable(pendingMovements);
        await customStatement(pendingMovementsKeepTheirContent);
      }
      if (from < 3) {
        await migrator.createTable(readSnapshots);
        await customStatement(readSnapshotsOnlyGetNewer);
      }
    },
  );
}

/// La base abierta y comprobada. Si falla —sin cifrado, o ilegible—, la app
/// muestra por qué y no continúa.
final FutureProvider<LocalDatabase> localDatabaseProvider =
    FutureProvider<LocalDatabase>((Ref ref) async {
      final LocalDatabase db = await ref
          .watch(localDatabaseOpenerProvider)
          .open(LocalDatabase.new);
      ref.onDispose(db.close);
      return db;
    });

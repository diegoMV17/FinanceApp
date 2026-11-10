import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/storage/local_database.dart';
import 'pending_movements.dart';

part 'local_database.g.dart';

/// La base local de la app, cifrada. La abre [LocalDatabaseOpener].
///
/// Versión 2: la cola de pendientes (7c). La 1, del 7b, no tenía tablas, pero
/// está en los teléfonos: se migra, no se recrea.
@DriftDatabase(tables: <Type>[PendingMovements])
class LocalDatabase extends _$LocalDatabase {
  LocalDatabase(super.executor);

  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (Migrator migrator) async {
      await migrator.createAll();
      await customStatement(pendingMovementsKeepTheirContent);
    },
    onUpgrade: (Migrator migrator, int from, int to) async {
      if (from < 2) {
        await migrator.createTable(pendingMovements);
        await customStatement(pendingMovementsKeepTheirContent);
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

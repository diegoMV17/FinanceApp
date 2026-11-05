import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/storage/local_database.dart';

part 'local_database.g.dart';

/// La base local de la app, cifrada. La abre [LocalDatabaseOpener].
@DriftDatabase(tables: <Type>[])
class LocalDatabase extends _$LocalDatabase {
  LocalDatabase(super.executor);

  @override
  int get schemaVersion => 1;
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

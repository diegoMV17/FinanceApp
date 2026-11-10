import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../data/local/pending_movements_store.dart';

/// Cuántos movimientos registrados en el teléfono todavía no llegaron al
/// servidor. El tablero lo dice junto a sus cifras, porque esos no cuentan en
/// ninguna: las cifras son las del servidor.
final StreamProvider<int> waitingMovementsProvider = StreamProvider<int>((
  Ref ref,
) async* {
  final PendingMovementsStore store = await ref.watch(
    pendingMovementsStoreProvider.future,
  );
  yield* store.counts.map((PendingCounts counts) => counts.waiting);
});

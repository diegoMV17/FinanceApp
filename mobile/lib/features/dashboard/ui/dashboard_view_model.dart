import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/ledger/book_changes.dart';
import '../../../core/lock/app_lock.dart';
import '../../../core/reading/reading.dart';
import '../data/dashboard.dart';
import '../data/dashboard_repository.dart';

/// El tablero. `null` mientras la app está bloqueada: no se lee nada hasta
/// desbloquear, y cada desbloqueo o cambio del libro (un movimiento que el
/// servidor aceptó, una cuenta, un presupuesto) vuelve a pedir.
class DashboardViewModel extends AsyncNotifier<Reading<Dashboard>?> {
  @override
  Future<Reading<Dashboard>?> build() async {
    final bool open = ref.watch(
      appLockProvider.select((AppLockState s) => s.isOpen),
    );
    ref.watch(bookChangesProvider);
    if (!open) {
      return null;
    }
    return (await ref.watch(dashboardRepositoryProvider.future)).load();
  }

  void refresh() => ref.invalidateSelf();
}

final AsyncNotifierProvider<DashboardViewModel, Reading<Dashboard>?>
dashboardViewModelProvider =
    AsyncNotifierProvider<DashboardViewModel, Reading<Dashboard>?>(
      DashboardViewModel.new,
    );

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/errors/request_failure.dart';
import '../../../core/ledger/account_balance.dart';
import '../../../core/ledger/month_summary.dart';
import '../../../core/reading/reading.dart';
import '../../../data/remote/book_service.dart';
import 'dashboard.dart';

/// El tablero, en cuatro lecturas del servidor y ninguna cuenta hecha aquí.
///
/// El resumen va primero porque fija el mes: los gastos por categoría se piden
/// de su `monthFrom` a su `asOf`, así que las dos cifras hablan siempre del
/// mismo mes aunque el teléfono y el servidor no estén de acuerdo en qué día
/// es.
class DashboardRepository {
  const DashboardRepository(this._book);

  final BookService _book;

  Future<Reading<Dashboard>> load() async {
    final Reading<MonthSummary> summary = await _book.summary();
    final (
      Reading<List<AccountBalance>> balances,
      Reading<List<MonthSummary>> overTime,
      Reading<List<CategoryTotal>> byCategory,
    ) = await (
      _book.balances(),
      _book.overTime(),
      _book.expensesByCategory(
        from: summary.value.monthFrom,
        to: summary.value.asOf,
      ),
    ).wait.onError<ParallelWaitError<Object?, Object?>>(
      (ParallelWaitError<Object?, Object?> failed, _) =>
          throw _firstFailure(failed),
    );

    final List<Reading<Object?>> parts = <Reading<Object?>>[
      summary,
      balances,
      overTime,
      byCategory,
    ];
    return Reading<Dashboard>(
      Dashboard(
        summary: summary.value,
        balances: balances.value,
        overTime: overTime.value,
        expensesByCategory: byCategory.value,
      ),
      // La hora que se muestra es la de la parte más vieja: decir «ahora»
      // con una parte de ayer sería mentir.
      parts
          .map((Reading<Object?> r) => r.readAt)
          .reduce((DateTime a, DateTime b) => a.isBefore(b) ? a : b),
      parts
          .map((Reading<Object?> r) => r.staleBecause)
          .firstWhere((RequestFailure? f) => f != null, orElse: () => null),
    );
  }

  static Object _firstFailure(ParallelWaitError<Object?, Object?> failed) {
    final Object? errors = failed.errors;
    if (errors is (Object?, Object?, Object?)) {
      return errors.$1 ?? errors.$2 ?? errors.$3 ?? failed;
    }
    return failed;
  }
}

final FutureProvider<DashboardRepository> dashboardRepositoryProvider =
    FutureProvider<DashboardRepository>(
      (Ref ref) async =>
          DashboardRepository(await ref.watch(bookServiceProvider.future)),
    );

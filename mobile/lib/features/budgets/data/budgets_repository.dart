import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/dates/calendar_date.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/budget.dart';
import '../../../core/reading/reading.dart';
import '../../../data/catalog/account_catalog.dart';
import '../../../data/remote/budgets_service.dart';

/// Los presupuestos: cómo va cada categoría en el mes, y poner un límite.
class BudgetsRepository {
  const BudgetsRepository(this._budgets, this._catalog);

  final BudgetsService _budgets;
  final AccountCatalog _catalog;

  Future<Reading<MonthBudgets>> budgets([YearMonth? month]) =>
      _budgets.budgets(month);

  Future<void> set(BudgetDraft budget) => _budgets.set(budget);

  /// Solo categorías de gasto abiertas: un límite sobre Nequi sería un tope a
  /// cuánta plata puede haber en el banco. El servidor lo rechazaría igual;
  /// esto solo evita ofrecerlo.
  Future<Reading<List<AccountRef>>> categories() async {
    final Reading<List<AccountRef>> all = await _catalog.accounts();
    return Reading<List<AccountRef>>(
      all.value
          .where(
            (AccountRef a) => a.kind == AccountKind.expense && !a.isArchived,
          )
          .toList(),
      all.readAt,
      all.staleBecause,
    );
  }
}

final FutureProvider<BudgetsRepository> budgetsRepositoryProvider =
    FutureProvider<BudgetsRepository>(
      (Ref ref) async => BudgetsRepository(
        await ref.watch(budgetsServiceProvider.future),
        await ref.watch(accountCatalogProvider.future),
      ),
    );

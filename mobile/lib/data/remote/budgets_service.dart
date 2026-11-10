import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/dates/calendar_date.dart';
import '../../core/ledger/budget.dart';
import '../../core/money/money.dart';
import '../../core/network/server_gateway.dart';
import '../../core/reading/reading.dart';
import '../api/export.dart';
import '../reading/read_through.dart';
import 'answers.dart';

/// Los presupuestos en el servidor: la lectura del mes con su copia, y poner
/// un límite en línea. Solo habla y traduce.
class BudgetsService {
  const BudgetsService(this._gateway, this._reader);

  final ServerGateway _gateway;
  final ReadThrough _reader;

  /// Sin [month], el mes en curso según el servidor, en la zona del dueño.
  Future<Reading<MonthBudgets>> budgets([YearMonth? month]) =>
      _reader.read<BudgetsResponse, MonthBudgets>(
        key: 'budgets/${month ?? 'current'}',
        fetch: (FinanceApi api) =>
            api.budgets.budgetsBudgets(month: month?.toString()),
        toJson: (BudgetsResponse r) => r.toJson(),
        fromJson: BudgetsResponse.fromJson,
        translate: (BudgetsResponse r) => MonthBudgets(
          YearMonth.parse(r.month),
          r.budgets.map(_progressOf).toList(),
        ),
      );

  Future<void> set(BudgetDraft budget) async {
    final BudgetResponse answer = await _gateway.call(
      (FinanceApi api) => api.budgets.budgetsSet(
        body: SetBudgetRequest(
          id: budget.id,
          category: budget.categoryId,
          month: budget.month.toString(),
          limitInCents: budget.limit.cents,
        ),
      ),
    );
    requireAnswerTo(answer.id, budget.id);
  }

  static BudgetProgress _progressOf(BudgetProgressResponse b) => BudgetProgress(
    id: b.id,
    categoryId: b.category,
    categoryName: b.categoryName,
    month: YearMonth.parse(b.month),
    limit: Money.ofCents(b.limitInCents),
    spent: Money.ofCents(b.spentInCents),
    remaining: Money.ofCents(b.remainingInCents),
    percentageUsed: b.percentageUsed,
    nearTheLimit: b.nearTheLimit,
    exceeded: b.exceeded,
  );
}

final FutureProvider<BudgetsService> budgetsServiceProvider =
    FutureProvider<BudgetsService>(
      (Ref ref) async => BudgetsService(
        await ref.watch(serverGatewayProvider.future),
        await ref.watch(readThroughProvider.future),
      ),
    );

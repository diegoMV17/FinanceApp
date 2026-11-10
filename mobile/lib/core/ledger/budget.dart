import '../dates/calendar_date.dart';
import '../ids/uuid.dart';
import '../money/amount_input.dart';
import '../money/money.dart';

/// Cómo va una categoría contra su límite del mes, como lo dice el servidor.
///
/// El porcentaje, [nearTheLimit] y [exceeded] viajan ya decididos y la app los
/// muestra tal cual: si los recalculara, el teléfono y el servidor avisarían
/// en días distintos del mismo mes.
class BudgetProgress {
  const BudgetProgress({
    required this.id,
    required this.categoryId,
    required this.categoryName,
    required this.month,
    required this.limit,
    required this.spent,
    required this.remaining,
    required this.percentageUsed,
    required this.nearTheLimit,
    required this.exceeded,
  });

  final String id;
  final String categoryId;
  final String categoryName;
  final YearMonth month;
  final Money limit;
  final Money spent;
  final Money remaining;
  final int percentageUsed;
  final bool nearTheLimit;
  final bool exceeded;
}

/// Los presupuestos de un mes.
class MonthBudgets {
  const MonthBudgets(this.month, this.budgets);

  final YearMonth month;
  final List<BudgetProgress> budgets;
}

/// Un límite por poner, con el id que generó el teléfono una vez por
/// formulario. Poner otro límite a la misma categoría y mes no edita el
/// anterior: el servidor registra uno nuevo que lo reemplaza.
class BudgetDraft {
  BudgetDraft({
    required this.id,
    required this.categoryId,
    required this.month,
    required this.limit,
  }) {
    if (!isUuidV4(id)) {
      throw ArgumentError.value('(oculto)', 'id', 'no es un UUID v4');
    }
    if (!isUuid(categoryId)) {
      throw ArgumentError.value('(oculto)', 'categoryId', 'no es un id');
    }
    if (!limit.isPositive || limit.cents > maxAmountCents) {
      throw ArgumentError.value('(oculto)', 'limit', 'fuera de 1..tope');
    }
  }

  final String id;
  final String categoryId;
  final YearMonth month;
  final Money limit;

  @override
  String toString() => 'BudgetDraft($id, $month)';
}

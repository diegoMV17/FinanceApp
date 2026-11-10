import '../dates/calendar_date.dart';
import '../money/money.dart';
import 'account_ref.dart';

/// Los indicadores a una fecha, como los da `GET /api/summary` y cada punto
/// de la serie. Todo lo calculó el servidor.
class MonthSummary {
  const MonthSummary({
    required this.asOf,
    required this.monthFrom,
    required this.moneyReallyMine,
    required this.income,
    required this.expenses,
  });

  final CalendarDate asOf;

  /// El primer día del mes de [asOf]: desde ahí cuentan [income] y [expenses].
  final CalendarDate monthFrom;

  /// La plata que es de verdad del dueño: lo que tiene menos lo que debe y lo
  /// que guarda de otros.
  final Money moneyReallyMine;
  final Money income;
  final Money expenses;
}

/// Cuánto se gastó en una categoría en el mes.
class CategoryTotal {
  const CategoryTotal(this.category, this.total);

  final AccountRef category;
  final Money total;
}

import '../../../core/ledger/account_balance.dart';
import '../../../core/ledger/month_summary.dart';

/// Lo que muestra el tablero.
class Dashboard {
  const Dashboard({
    required this.summary,
    required this.balances,
    required this.overTime,
    required this.expensesByCategory,
  });

  final MonthSummary summary;
  final List<AccountBalance> balances;

  /// Un punto por fin de mes del último año; el último, hoy.
  final List<MonthSummary> overTime;

  /// La más grande primero, como las ordena el servidor.
  final List<CategoryTotal> expensesByCategory;
}

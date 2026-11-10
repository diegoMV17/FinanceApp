import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/budget.dart';
import 'package:mobile/core/ledger/month_summary.dart';
import 'package:mobile/core/ledger/person.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/features/accounts/ui/accounts_view_model.dart';
import 'package:mobile/features/budgets/ui/budgets_view_model.dart';
import 'package:mobile/features/dashboard/data/dashboard.dart';
import 'package:mobile/features/dashboard/data/waiting_movements.dart';
import 'package:mobile/features/dashboard/ui/dashboard_view_model.dart';
import 'package:mobile/features/people/ui/people_view_model.dart';

/// Lecturas fijas para los tests de widgets: Drift no corre bajo el reloj
/// falso de `testWidgets`, y lo que hacen los view models de verdad está
/// probado sin widgets.
final DateTime readAt = DateTime.utc(2026, 11, 9, 14);

Dashboard emptyDashboard({Money? mine}) {
  final MonthSummary summary = MonthSummary(
    asOf: CalendarDate(2026, 11, 9),
    monthFrom: CalendarDate(2026, 11, 1),
    moneyReallyMine: mine ?? Money.zero,
    income: Money.zero,
    expenses: Money.zero,
  );
  return Dashboard(
    summary: summary,
    balances: const [],
    overTime: <MonthSummary>[summary],
    expensesByCategory: const <CategoryTotal>[],
  );
}

class FixedDashboard extends DashboardViewModel {
  FixedDashboard(this.reading);

  final Reading<Dashboard>? reading;

  @override
  Future<Reading<Dashboard>?> build() async => reading;
}

class FixedPeople extends PeopleViewModel {
  FixedPeople(this.reading);

  final Reading<List<PersonPosition>>? reading;

  @override
  Future<Reading<List<PersonPosition>>?> build() async => reading;
}

class FixedBudgets extends BudgetsViewModel {
  FixedBudgets(this.reading);

  final Reading<MonthBudgets>? reading;

  @override
  Future<Reading<MonthBudgets>?> build() async => reading;
}

class FixedAccounts extends AccountsViewModel {
  FixedAccounts(this.reading);

  final Reading<List<AccountRef>>? reading;

  @override
  Future<Reading<List<AccountRef>>?> build() async => reading;
}

/// Todas las lecturas de adentro, fijas, más el número de pendientes.
List<Override> fixedScreens({
  Reading<Dashboard>? dashboard,
  Reading<List<PersonPosition>>? people,
  Reading<MonthBudgets>? budgets,
  int waiting = 0,
}) => <Override>[
  dashboardViewModelProvider.overrideWith(
    () => FixedDashboard(
      dashboard ?? Reading<Dashboard>(emptyDashboard(), readAt),
    ),
  ),
  peopleViewModelProvider.overrideWith(
    () => FixedPeople(
      people ?? Reading<List<PersonPosition>>(const <PersonPosition>[], readAt),
    ),
  ),
  budgetsViewModelProvider.overrideWith(
    () => FixedBudgets(
      budgets ??
          Reading<MonthBudgets>(
            MonthBudgets(YearMonth(2026, 11), const <BudgetProgress>[]),
            readAt,
          ),
    ),
  ),
  accountsViewModelProvider.overrideWith(
    () =>
        FixedAccounts(Reading<List<AccountRef>>(const <AccountRef>[], readAt)),
  ),
  waitingMovementsProvider.overrideWith(
    (Ref ref) => Stream<int>.value(waiting),
  ),
];

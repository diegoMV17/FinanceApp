import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/dates/calendar_date.dart';
import '../../core/ledger/account_balance.dart';
import '../../core/ledger/account_ref.dart';
import '../../core/ledger/month_summary.dart';
import '../../core/money/money.dart';
import '../../core/reading/reading.dart';
import '../api/export.dart';
import '../catalog/account_catalog.dart';
import '../reading/read_through.dart';

/// Las lecturas del libro que dibuja el tablero. Solo hablan y traducen:
/// cada una guarda su última copia y, sin servidor, la devuelve.
class BookService {
  const BookService(this._reader);

  final ReadThrough _reader;

  Future<Reading<MonthSummary>> summary() =>
      _reader.read<SummaryResponse, MonthSummary>(
        key: 'summary',
        fetch: (FinanceApi api) => api.book.bookSummary(),
        toJson: (SummaryResponse r) => r.toJson(),
        fromJson: SummaryResponse.fromJson,
        translate: _summaryOf,
      );

  Future<Reading<List<AccountBalance>>> balances() =>
      _reader.read<BalancesResponse, List<AccountBalance>>(
        key: balancesKey,
        fetch: (FinanceApi api) => api.book.bookBalances(),
        toJson: (BalancesResponse r) => r.toJson(),
        fromJson: BalancesResponse.fromJson,
        translate: (BalancesResponse r) =>
            r.accounts.map(accountBalanceOf).toList(),
      );

  /// Sin fechas: el año hacia atrás lo decide el servidor, en la zona del
  /// dueño.
  Future<Reading<List<MonthSummary>>> overTime() =>
      _reader.read<OverTimeResponse, List<MonthSummary>>(
        key: 'over-time',
        fetch: (FinanceApi api) => api.stats.statsOverTime(),
        toJson: (OverTimeResponse r) => r.toJson(),
        fromJson: OverTimeResponse.fromJson,
        translate: (OverTimeResponse r) => r.points.map(_summaryOf).toList(),
      );

  /// Lo que se gastó en cada categoría entre [from] y [to].
  Future<Reading<List<CategoryTotal>>> expensesByCategory({
    required CalendarDate from,
    required CalendarDate to,
  }) => _reader.read<TotalsByAccountResponse, List<CategoryTotal>>(
    key: 'expenses-by-category',
    fetch: (FinanceApi api) => api.stats.statsByAccount(
      from: from.toString(),
      to: to.toString(),
      type: AccountType.expense,
    ),
    toJson: (TotalsByAccountResponse r) => r.toJson(),
    fromJson: TotalsByAccountResponse.fromJson,
    translate: (TotalsByAccountResponse r) => <CategoryTotal>[
      for (final AccountTotalResponse total in r.accounts)
        CategoryTotal(
          AccountRef(
            id: total.id,
            name: total.name,
            kind: AccountKind.fromWire(total.type.json ?? ''),
            isArchived: total.archivedAt != null,
          ),
          Money.ofCents(total.totalInCents),
        ),
    ],
  );

  static MonthSummary _summaryOf(SummaryResponse r) => MonthSummary(
    asOf: CalendarDate.parse(r.asOf),
    monthFrom: CalendarDate.parse(r.monthFrom),
    moneyReallyMine: Money.ofCents(r.moneyReallyMineInCents),
    income: Money.ofCents(r.incomeThatMonthInCents),
    expenses: Money.ofCents(r.expensesThatMonthInCents),
  );
}

final FutureProvider<BookService> bookServiceProvider =
    FutureProvider<BookService>(
      (Ref ref) async =>
          BookService(await ref.watch(readThroughProvider.future)),
    );

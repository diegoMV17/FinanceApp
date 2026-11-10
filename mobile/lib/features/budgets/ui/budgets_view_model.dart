import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/dates/calendar_date.dart';
import '../../../core/forms/submit_state.dart';
import '../../../core/forms/write_once.dart';
import '../../../core/ids/uuid.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/book_changes.dart';
import '../../../core/ledger/budget.dart';
import '../../../core/lock/app_lock.dart';
import '../../../core/money/amount_input.dart';
import '../../../core/money/money.dart';
import '../../../core/reading/reading.dart';
import '../data/budgets_repository.dart';

/// El mes que se está mirando. `null` es el mes en curso según el servidor.
class SelectedBudgetMonth extends Notifier<YearMonth?> {
  @override
  YearMonth? build() => null;

  void show(YearMonth? month) => state = month;
}

final NotifierProvider<SelectedBudgetMonth, YearMonth?>
selectedBudgetMonthProvider = NotifierProvider<SelectedBudgetMonth, YearMonth?>(
  SelectedBudgetMonth.new,
);

/// Cómo va cada categoría en el mes. `null` mientras la app está bloqueada.
class BudgetsViewModel extends AsyncNotifier<Reading<MonthBudgets>?> {
  @override
  Future<Reading<MonthBudgets>?> build() async {
    final bool open = ref.watch(
      appLockProvider.select((AppLockState s) => s.isOpen),
    );
    ref.watch(bookChangesProvider);
    final YearMonth? month = ref.watch(selectedBudgetMonthProvider);
    if (!open) {
      return null;
    }
    return (await ref.watch(budgetsRepositoryProvider.future)).budgets(month);
  }

  void refresh() => ref.invalidateSelf();
}

final AsyncNotifierProvider<BudgetsViewModel, Reading<MonthBudgets>?>
budgetsViewModelProvider =
    AsyncNotifierProvider<BudgetsViewModel, Reading<MonthBudgets>?>(
      BudgetsViewModel.new,
    );

/// Las categorías a las que se les puede poner un límite.
final FutureProvider<Reading<List<AccountRef>>> budgetCategoriesProvider =
    FutureProvider.autoDispose<Reading<List<AccountRef>>>(
      (Ref ref) async =>
          (await ref.watch(budgetsRepositoryProvider.future)).categories(),
    );

/// Poner un límite. El id, una vez por formulario.
class SetBudgetViewModel extends Notifier<SubmitState> {
  final String _id = generateUuidV4();

  @override
  SubmitState build() => const SubmitState.editing();

  Future<void> submit({
    required String? categoryId,
    required YearMonth month,
    required String limit,
  }) async {
    if (state.isSubmitting || state.isDone) {
      return;
    }
    if (categoryId == null) {
      state = const SubmitState.editing('Elige la categoría.');
      return;
    }
    final Money amount;
    try {
      amount = parseAmount(limit);
    } on AmountInputException catch (invalid) {
      state = SubmitState.editing(amountMessage(invalid.problem));
      return;
    }
    final BudgetDraft draft = BudgetDraft(
      id: _id,
      categoryId: categoryId,
      month: month,
      limit: amount,
    );
    state = const SubmitState.submitting();
    final SubmitState result = await writeOnce(
      () async => (await ref.read(budgetsRepositoryProvider.future)).set(draft),
    );
    if (!ref.mounted) {
      return;
    }
    if (result.isDone) {
      ref.read(bookChangesProvider.notifier).changed();
    }
    state = result;
  }
}

final NotifierProvider<SetBudgetViewModel, SubmitState>
setBudgetViewModelProvider =
    NotifierProvider.autoDispose<SetBudgetViewModel, SubmitState>(
      SetBudgetViewModel.new,
    );

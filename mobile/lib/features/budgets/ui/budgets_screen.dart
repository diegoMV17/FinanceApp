import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/dates/calendar_date.dart';
import '../../../core/dates/time_label.dart';
import '../../../core/forms/submit_state.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/budget.dart';
import '../../../core/money/money_format.dart';
import '../../../core/reading/reading.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/theme/finance_colors.dart';
import '../../../core/widgets/components.dart';
import '../../../core/widgets/reading_notices.dart';
import 'budgets_view_model.dart';

/// Cómo va cada categoría contra su límite, mes a mes.
///
/// El porcentaje, «cerca del límite» y «excedido» son los del servidor: la
/// app no los recalcula, para no avisar en un día distinto del suyo.
class BudgetsScreen extends ConsumerWidget {
  const BudgetsScreen({super.key});

  static const Key setBudgetKey = Key('set-budget');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<Reading<MonthBudgets>?> budgets = ref.watch(
      budgetsViewModelProvider,
    );
    final YearMonth? shown = budgets.value?.value.month;
    return Scaffold(
      appBar: AppBar(title: const Text('Presupuestos')),
      floatingActionButton: shown == null
          ? null
          : FloatingActionButton.extended(
              key: setBudgetKey,
              onPressed: () => _openSetBudget(context, shown),
              icon: const Icon(Icons.add),
              label: const Text('Límite'),
            ),
      body: switch (budgets) {
        AsyncData<Reading<MonthBudgets>?>(
          value: final Reading<MonthBudgets> r,
        ) =>
          ListView(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 104),
            children: <Widget>[
              StaleNotice(reading: r),
              _MonthPicker(month: r.value.month),
              const SizedBox(height: 8),
              if (r.value.budgets.isEmpty)
                Card(
                  child: EmptyState(
                    icon: Icons.savings_outlined,
                    title: 'Sin presupuestos este mes',
                    message:
                        'Ponle un límite a una categoría de gasto y aquí '
                        'verás cómo va.',
                    actionLabel: 'Poner un límite',
                    onAction: () => _openSetBudget(context, r.value.month),
                  ),
                ),
              for (final BudgetProgress budget in r.value.budgets)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: BudgetTile(budget: budget),
                ),
            ],
          ),
        AsyncError<Reading<MonthBudgets>?>(:final Object error) =>
          FailureNotice(
            failure: error,
            onRetry: () =>
                ref.read(budgetsViewModelProvider.notifier).refresh(),
          ),
        _ => const LoadingBlocks(heights: <double>[56, 120, 120]),
      },
    );
  }

  static void _openSetBudget(BuildContext context, YearMonth month) =>
      showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        builder: (_) => _SetBudgetSheet(month: month),
      );
}

class _MonthPicker extends ConsumerWidget {
  const _MonthPicker({required this.month});

  final YearMonth month;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final SelectedBudgetMonth selected = ref.read(
      selectedBudgetMonthProvider.notifier,
    );
    return Card(
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: <Widget>[
          IconButton(
            tooltip: 'Mes anterior',
            icon: const Icon(Icons.chevron_left),
            onPressed: () => selected.show(month.minusMonths(1)),
          ),
          TextButton(
            // Tocar el mes vuelve al mes en curso, el que dice el servidor.
            onPressed: () => selected.show(null),
            child: Text(
              monthLabel(month),
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
          IconButton(
            tooltip: 'Mes siguiente',
            icon: const Icon(Icons.chevron_right),
            onPressed: () => selected.show(month.minusMonths(-1)),
          ),
        ],
      ),
    );
  }
}

/// Una categoría: lo gastado contra el límite, con la barra en el
/// porcentaje del servidor.
class BudgetTile extends StatelessWidget {
  const BudgetTile({required this.budget, super.key});

  static const Key exceededKey = Key('budget-exceeded');
  static const Key nearKey = Key('budget-near');

  final BudgetProgress budget;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final FinanceColors finance = context.financeColors;
    // El color sale de los avisos del servidor, no del porcentaje: así el
    // teléfono y el servidor avisan el mismo día.
    final Color bar = budget.exceeded
        ? finance.expense
        : budget.nearTheLimit
        ? finance.warning
        : theme.colorScheme.primary;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Expanded(
                  child: Text(
                    budget.categoryName,
                    style: theme.textTheme.titleMedium,
                  ),
                ),
                if (budget.exceeded)
                  _Flag(
                    key: exceededKey,
                    label: 'Excedido',
                    color: finance.expense,
                  )
                else if (budget.nearTheLimit)
                  _Flag(
                    key: nearKey,
                    label: 'Cerca del límite',
                    color: finance.warning,
                  ),
              ],
            ),
            const SizedBox(height: 12),
            // Solo la longitud de la barra sale de dividir; la cifra que se lee
            // es el porcentaje entero del servidor.
            LinearProgressIndicator(
              value: (budget.percentageUsed.clamp(0, 100)) / 100,
              color: bar,
            ),
            const SizedBox(height: 10),
            Text(
              '${budget.spent.inPesos} de ${budget.limit.inPesos} · '
              '${budget.percentageUsed} %',
              style: moneyStyle(theme.textTheme.bodyMedium!),
            ),
            const SizedBox(height: 2),
            Text(
              'Queda ${budget.remaining.inPesos}',
              style: moneyStyle(theme.textTheme.bodySmall!)
                  .copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }
}

/// El aviso del servidor, como una etiqueta con su color.
class _Flag extends StatelessWidget {
  const _Flag({required this.label, required this.color, super.key});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(
      color: color.withValues(alpha: 0.12),
      borderRadius: BorderRadius.circular(999),
    ),
    child: Text(
      label,
      style: Theme.of(context).textTheme.labelMedium
          ?.copyWith(color: color, fontWeight: FontWeight.w600),
    ),
  );
}

class _SetBudgetSheet extends ConsumerStatefulWidget {
  const _SetBudgetSheet({required this.month});

  final YearMonth month;

  @override
  ConsumerState<_SetBudgetSheet> createState() => _SetBudgetSheetState();
}

class _SetBudgetSheetState extends ConsumerState<_SetBudgetSheet> {
  final TextEditingController _limit = TextEditingController();
  String? _category;

  @override
  void dispose() {
    _limit.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final SubmitState state = ref.watch(setBudgetViewModelProvider);
    final List<AccountRef> categories =
        ref.watch(budgetCategoriesProvider).value?.value ?? <AccountRef>[];
    ref.listen(setBudgetViewModelProvider, (_, SubmitState next) {
      if (next.isDone) {
        Navigator.of(context).pop();
      }
    });
    return Padding(
      padding: EdgeInsets.fromLTRB(
        24,
        24,
        24,
        24 + MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Text(
            'Límite para ${monthLabel(widget.month)}',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _category,
            decoration: const InputDecoration(labelText: 'Categoría'),
            items: <DropdownMenuItem<String>>[
              for (final AccountRef c in categories)
                DropdownMenuItem<String>(value: c.id, child: Text(c.name)),
            ],
            onChanged: (String? id) => setState(() => _category = id),
          ),
          TextField(
            controller: _limit,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(
              labelText: 'Límite en pesos',
              prefixText: r'$ ',
            ),
          ),
          if (state.error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(
                state.error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: state.isSubmitting
                ? null
                : () => ref
                      .read(setBudgetViewModelProvider.notifier)
                      .submit(
                        categoryId: _category,
                        month: widget.month,
                        limit: _limit.text,
                      ),
            child: const Text('Poner límite'),
          ),
        ],
      ),
    );
  }
}

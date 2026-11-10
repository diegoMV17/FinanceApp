import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/dates/calendar_date.dart';
import '../../../core/dates/clock.dart';
import '../../../core/dates/time_label.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/reading/reading.dart';
import '../../../core/text/text_input.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/account_icons.dart';
import '../../../core/widgets/components.dart';
import '../../../core/widgets/reading_notices.dart';
import 'record_movement_view_model.dart';

/// Registrar un gasto, un ingreso, una transferencia o un saldo inicial.
///
/// Registrar es guardarlo en la cola del teléfono: sale cuando haya conexión,
/// y cuenta en el tablero cuando el servidor lo aceptó.
class RecordMovementScreen extends ConsumerStatefulWidget {
  const RecordMovementScreen({required this.onOpenAccounts, super.key});

  static const Key amountFieldKey = Key('movement-amount');
  static const Key descriptionFieldKey = Key('movement-description');
  static const Key firstFieldKey = Key('movement-first');
  static const Key secondFieldKey = Key('movement-second');
  static const Key submitKey = Key('movement-submit');

  /// Ir a abrir cuentas: es de otra funcionalidad.
  final VoidCallback onOpenAccounts;

  @override
  ConsumerState<RecordMovementScreen> createState() =>
      _RecordMovementScreenState();
}

class _RecordMovementScreenState extends ConsumerState<RecordMovementScreen> {
  final TextEditingController _amount = TextEditingController();
  final TextEditingController _description = TextEditingController();

  @override
  void dispose() {
    _amount.dispose();
    _description.dispose();
    super.dispose();
  }

  RecordMovementViewModel get _viewModel =>
      ref.read(recordMovementViewModelProvider.notifier);

  @override
  Widget build(BuildContext context) {
    final RecordMovementState state = ref.watch(
      recordMovementViewModelProvider,
    );
    final AsyncValue<Reading<List<AccountRef>>> accounts = ref.watch(
      recordableAccountsProvider,
    );
    ref.listen(recordMovementViewModelProvider, (
      RecordMovementState? previous,
      RecordMovementState next,
    ) {
      if (next.submit.isDone && !(previous?.submit.isDone ?? false)) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Registrado en el teléfono. Se envía en cuanto haya conexión.',
            ),
          ),
        );
        Navigator.of(context).pop();
      }
    });
    return Scaffold(
      appBar: AppBar(title: const Text('Registrar')),
      body: switch (accounts) {
        AsyncData<Reading<List<AccountRef>>>(
          value: final Reading<List<AccountRef>> r,
        ) =>
          _form(context, state, r),
        AsyncError<Reading<List<AccountRef>>>(:final Object error) =>
          FailureNotice(
            failure: error,
            onRetry: () => ref.invalidate(recordableAccountsProvider),
          ),
        _ => const LoadingBlocks(heights: <double>[96, 56, 120, 120]),
      },
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: EdgeInsets.fromLTRB(
            16,
            8,
            16,
            16 + MediaQuery.viewInsetsOf(context).bottom,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              if (state.submit.error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Text(
                    state.submit.error!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ),
              FilledButton.icon(
                key: RecordMovementScreen.submitKey,
                onPressed: state.submit.isSubmitting
                    ? null
                    : () => _viewModel.submit(
                        amount: _amount.text,
                        description: state.kind.hasDescription
                            ? _description.text
                            : '',
                      ),
                icon: const Icon(Icons.check),
                label: const Text('Registrar'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _form(
    BuildContext context,
    RecordMovementState state,
    Reading<List<AccountRef>> accounts,
  ) {
    final ThemeData theme = Theme.of(context);
    final MovementKind kind = state.kind;
    final List<AccountRef> firsts = kind.firstOptions(accounts.value);
    final List<AccountRef> seconds = kind.secondOptions(accounts.value);
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
      children: <Widget>[
        StaleNotice(reading: accounts),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: <Widget>[
            for (final MovementKind k in MovementKind.values)
              ChoiceChip(
                label: Text(k.label),
                avatar: Icon(_kindIcon(k), size: 18),
                showCheckmark: false,
                selected: k == kind,
                onSelected: (_) => _viewModel.chooseKind(k),
              ),
          ],
        ),
        const SizedBox(height: 24),
        TextField(
          key: RecordMovementScreen.amountFieldKey,
          controller: _amount,
          autofocus: true,
          textAlign: TextAlign.center,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          style: moneyStyle(theme.textTheme.displaySmall!)
              .copyWith(fontWeight: FontWeight.w700),
          decoration: const InputDecoration(
            hintText: '0',
            prefixText: r'$ ',
            filled: false,
            border: InputBorder.none,
            focusedBorder: InputBorder.none,
            helperText: 'Pesos, con punto de miles y coma decimal',
          ),
        ),
        const SizedBox(height: 16),
        if (firsts.isEmpty || seconds.isEmpty)
          Card(
            child: EmptyState(
              icon: Icons.account_balance_wallet_outlined,
              title: 'Faltan cuentas para esto',
              message:
                  'Para «${kind.label}» hace falta al menos una en '
                  '«${kind.firstLabel}» y otra en «${kind.secondLabel}».',
              actionLabel: 'Abrir cuentas',
              onAction: widget.onOpenAccounts,
            ),
          )
        else ...<Widget>[
          _AccountChoice(
            key: RecordMovementScreen.firstFieldKey,
            label: kind.firstLabel,
            options: firsts,
            selected: state.first,
            onChanged: _viewModel.chooseFirst,
          ),
          const SizedBox(height: 16),
          _AccountChoice(
            key: RecordMovementScreen.secondFieldKey,
            label: kind.secondLabel,
            options: seconds,
            selected: state.second,
            onChanged: _viewModel.chooseSecond,
          ),
        ],
        const SizedBox(height: 16),
        if (kind.hasDescription)
          TextField(
            key: RecordMovementScreen.descriptionFieldKey,
            controller: _description,
            maxLength: maxDescriptionLength,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(
              labelText: 'Descripción',
              prefixIcon: Icon(Icons.notes),
            ),
          ),
        _DateChoice(
          date: state.date,
          today: CalendarDate.ofLocal(ref.read(clockProvider)()),
          onDaysAgo: _viewModel.chooseDaysAgo,
          onOther: () => _pickDate(context, state.date),
        ),
      ],
    );
  }

  static IconData _kindIcon(MovementKind kind) => switch (kind) {
    MovementKind.expense => Icons.north_east,
    MovementKind.income => Icons.south_west,
    MovementKind.transfer => Icons.swap_horiz,
    MovementKind.openingBalance => Icons.flag_outlined,
  };

  Future<void> _pickDate(BuildContext context, CalendarDate current) async {
    final DateTime? picked = await showDatePicker(
      context: context,
      initialDate: DateTime(current.year, current.month, current.day),
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
    );
    if (picked != null) {
      // El selector devuelve una hora local a medianoche: su día es el
      // elegido.
      _viewModel.chooseDate(CalendarDate.ofLocal(picked));
    }
  }
}

/// Un hueco del movimiento: las cuentas que sirven, como chips.
class _AccountChoice extends StatelessWidget {
  const _AccountChoice({
    required this.label,
    required this.options,
    required this.selected,
    required this.onChanged,
    super.key,
  });

  final String label;
  final List<AccountRef> options;
  final String? selected;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(label, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: <Widget>[
            for (final AccountRef account in options)
              ChoiceChip(
                avatar: Icon(iconForKind(account.kind), size: 18),
                label: Text(account.name),
                showCheckmark: false,
                selected: account.id == selected,
                onSelected: (bool on) => onChanged(on ? account.id : null),
              ),
          ],
        ),
      ],
    );
  }
}

/// La fecha: hoy, ayer u otra del calendario.
class _DateChoice extends StatelessWidget {
  const _DateChoice({
    required this.date,
    required this.today,
    required this.onDaysAgo,
    required this.onOther,
  });

  final CalendarDate date;

  /// El día del reloj del teléfono, el mismo que usa el view model.
  final CalendarDate today;
  final ValueChanged<int> onDaysAgo;
  final VoidCallback onOther;

  @override
  Widget build(BuildContext context) {
    final CalendarDate yesterday = CalendarDate.ofLocal(
      DateTime(today.year, today.month, today.day - 1),
    );
    final bool isToday = date == today;
    final bool isYesterday = date == yesterday;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text('Fecha', style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          children: <Widget>[
            ChoiceChip(
              label: const Text('Hoy'),
              showCheckmark: false,
              selected: isToday,
              onSelected: (_) => onDaysAgo(0),
            ),
            ChoiceChip(
              label: const Text('Ayer'),
              showCheckmark: false,
              selected: isYesterday,
              onSelected: (_) => onDaysAgo(1),
            ),
            ChoiceChip(
              avatar: const Icon(Icons.event, size: 18),
              label: Text(isToday || isYesterday ? 'Otra' : dateLabel(date)),
              showCheckmark: false,
              selected: !isToday && !isYesterday,
              onSelected: (_) => onOther(),
            ),
          ],
        ),
      ],
    );
  }
}

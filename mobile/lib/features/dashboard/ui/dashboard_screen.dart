import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/ledger/account_balance.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/money/money_format.dart';
import '../../../core/reading/reading.dart';
import '../../../core/widgets/reading_notices.dart';
import '../data/dashboard.dart';
import '../data/waiting_movements.dart';
import 'dashboard_charts.dart';
import 'dashboard_view_model.dart';

/// El tablero: las cifras del servidor, tal cual, con su fecha.
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({required this.onOpenAccounts, super.key});

  static const Key moneyReallyMineKey = Key('money-really-mine');
  static const Key waitingKey = Key('waiting-not-counted');

  /// Ir a las cuentas: es de otra funcionalidad, así que lo resuelve quien
  /// arma la app.
  final VoidCallback onOpenAccounts;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<Reading<Dashboard>?> dashboard = ref.watch(
      dashboardViewModelProvider,
    );
    final int waiting = ref.watch(waitingMovementsProvider).value ?? 0;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Tablero'),
        actions: <Widget>[
          IconButton(
            tooltip: 'Cuentas',
            icon: const Icon(Icons.account_balance_wallet_outlined),
            onPressed: onOpenAccounts,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.read(dashboardViewModelProvider.notifier).refresh();
          await ref.read(dashboardViewModelProvider.future);
        },
        child: switch (dashboard) {
          AsyncData<Reading<Dashboard>?>(value: final Reading<Dashboard> r) =>
            _Content(reading: r, waiting: waiting),
          AsyncError<Reading<Dashboard>?>(:final Object error) => ListView(
            children: <Widget>[
              FailureNotice(
                failure: error,
                onRetry: () =>
                    ref.read(dashboardViewModelProvider.notifier).refresh(),
              ),
            ],
          ),
          _ => const Center(child: CircularProgressIndicator()),
        },
      ),
    );
  }
}

class _Content extends StatelessWidget {
  const _Content({required this.reading, required this.waiting});

  final Reading<Dashboard> reading;
  final int waiting;

  @override
  Widget build(BuildContext context) {
    final Dashboard dashboard = reading.value;
    final TextTheme text = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: <Widget>[
        StaleNotice(reading: reading),
        if (waiting > 0)
          Card(
            key: DashboardScreen.waitingKey,
            child: ListTile(
              leading: const Icon(Icons.schedule_send_outlined),
              title: Text(
                waiting == 1
                    ? '1 movimiento pendiente todavía no cuenta en estas cifras.'
                    : '$waiting movimientos pendientes todavía no cuentan en '
                          'estas cifras.',
              ),
            ),
          ),
        Text('Plata realmente mía', style: text.titleMedium),
        Text(
          dashboard.summary.moneyReallyMine.inPesos,
          key: DashboardScreen.moneyReallyMineKey,
          style: text.headlineMedium,
        ),
        const SizedBox(height: 8),
        Row(
          children: <Widget>[
            Expanded(
              child: _Figure(
                label: 'Ingresos del mes',
                value: dashboard.summary.income.inPesos,
              ),
            ),
            Expanded(
              child: _Figure(
                label: 'Gastos del mes',
                value: dashboard.summary.expenses.inPesos,
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),
        Text('Último año', style: text.titleMedium),
        OverTimeChart(points: dashboard.overTime),
        const SizedBox(height: 24),
        Text('Gastos del mes por categoría', style: text.titleMedium),
        CategoryChart(totals: dashboard.expensesByCategory),
        const SizedBox(height: 24),
        Text('Saldos', style: text.titleMedium),
        for (final AccountBalance line in _visible(dashboard.balances))
          ListTile(
            title: Text(line.account.name),
            subtitle: Text(line.account.kind.label),
            trailing: Text(line.balance.inPesos),
          ),
      ],
    );
  }

  /// Las cuentas de plata, lo que me deben, lo que debo y la plata de otros;
  /// las categorías tienen su gráfico. Las archivadas no se listan.
  static Iterable<AccountBalance> _visible(List<AccountBalance> all) =>
      all.where(
        (AccountBalance b) =>
            !b.account.isArchived &&
            b.account.kind != AccountKind.expense &&
            b.account.kind != AccountKind.income &&
            b.account.kind != AccountKind.equity,
      );
}

class _Figure extends StatelessWidget {
  const _Figure({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(label, style: text.labelMedium),
        Text(value, style: text.titleMedium),
      ],
    );
  }
}

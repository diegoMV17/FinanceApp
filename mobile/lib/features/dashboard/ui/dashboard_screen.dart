import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/dates/time_label.dart';
import '../../../core/ledger/account_balance.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/money/money_format.dart';
import '../../../core/reading/reading.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/theme/finance_colors.dart';
import '../../../core/widgets/account_icons.dart';
import '../../../core/widgets/components.dart';
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
            tooltip: 'Cuentas y categorías',
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
            _Content(
              reading: r,
              waiting: waiting,
              onOpenAccounts: onOpenAccounts,
            ),
          AsyncError<Reading<Dashboard>?>(:final Object error) => ListView(
            children: <Widget>[
              FailureNotice(
                failure: error,
                onRetry: () =>
                    ref.read(dashboardViewModelProvider.notifier).refresh(),
              ),
            ],
          ),
          _ => const LoadingBlocks(),
        },
      ),
    );
  }
}

class _Content extends StatelessWidget {
  const _Content({
    required this.reading,
    required this.waiting,
    required this.onOpenAccounts,
  });

  final Reading<Dashboard> reading;
  final int waiting;
  final VoidCallback onOpenAccounts;

  static const SizedBox _gap = SizedBox(height: 12);

  @override
  Widget build(BuildContext context) {
    final Dashboard dashboard = reading.value;
    final List<AccountBalance> balances = _visible(dashboard.balances).toList();
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 104),
      children: <Widget>[
        StaleNotice(reading: reading),
        if (waiting > 0) ...<Widget>[
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
          _gap,
        ],
        // Sin cuentas no hay nada que mirar todavía: lo primero es abrirlas.
        if (balances.isEmpty) ...<Widget>[
          Card(
            child: EmptyState(
              icon: Icons.account_balance_wallet_outlined,
              title: 'Todavía no hay cuentas',
              message:
                  'Abre las cuentas donde tienes plata y las categorías de '
                  'gasto e ingreso para empezar a registrar.',
              actionLabel: 'Abrir una cuenta',
              onAction: onOpenAccounts,
            ),
          ),
          _gap,
        ],
        _Hero(dashboard: dashboard),
        _gap,
        Row(
          children: <Widget>[
            Expanded(
              child: StatTile(
                label: 'Ingresos del mes',
                amount: dashboard.summary.income,
                role: MoneyRole.income,
                icon: Icons.south_west,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: StatTile(
                label: 'Gastos del mes',
                amount: dashboard.summary.expenses,
                role: MoneyRole.expense,
                icon: Icons.north_east,
              ),
            ),
          ],
        ),
        _gap,
        SectionCard(
          title: 'Último año',
          child: OverTimeChart(points: dashboard.overTime),
        ),
        _gap,
        SectionCard(
          title: 'Gastos del mes por categoría',
          child: CategoryChart(
            totals: dashboard.expensesByCategory,
            total: dashboard.summary.expenses,
          ),
        ),
        _gap,
        if (balances.isNotEmpty)
          SectionCard(
            title: 'Saldos',
            padding: const EdgeInsets.fromLTRB(0, 16, 0, 8),
            child: Column(
              children: <Widget>[
                for (final AccountBalance line in balances)
                  ListTile(
                    leading: CircleAvatar(
                      backgroundColor: Theme.of(context)
                          .colorScheme
                          .secondaryContainer,
                      child: Icon(
                        iconForKind(line.account.kind),
                        color: Theme.of(context)
                            .colorScheme
                            .onSecondaryContainer,
                        size: 20,
                      ),
                    ),
                    title: Text(line.account.name),
                    subtitle: Text(line.account.kind.label),
                    trailing: MoneyText(line.balance),
                  ),
              ],
            ),
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

/// La cifra principal, sobre el verde de la app.
class _Hero extends StatelessWidget {
  const _Hero({required this.dashboard});

  final Dashboard dashboard;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme colors = theme.colorScheme;
    return Card(
      color: colors.primary,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              'Plata realmente mía',
              style: theme.textTheme.titleSmall?.copyWith(
                color: colors.onPrimary.withValues(alpha: 0.85),
              ),
            ),
            const SizedBox(height: 8),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(
                dashboard.summary.moneyReallyMine.inPesos,
                key: DashboardScreen.moneyReallyMineKey,
                style: moneyStyle(theme.textTheme.displaySmall!).copyWith(
                  color: colors.onPrimary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Al ${dateLabel(dashboard.summary.asOf)}',
              style: theme.textTheme.bodySmall?.copyWith(
                color: colors.onPrimary.withValues(alpha: 0.75),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

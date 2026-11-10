import 'package:flutter/material.dart';

import 'core/storage/server_settings.dart';
import 'core/widgets/components.dart';
import 'features/accounts/ui/accounts_screen.dart';
import 'features/budgets/ui/budgets_screen.dart';
import 'features/dashboard/ui/dashboard_screen.dart';
import 'features/movements/ui/pending_status_view.dart';
import 'features/movements/ui/record_movement_screen.dart';
import 'features/people/ui/people_screen.dart';
import 'features/settings/ui/connection_status_view.dart';

/// Adentro: las pestañas, la franja de pendientes y el botón de registrar.
///
/// Vive en la raíz y no en una funcionalidad porque es lo que las une: una
/// funcionalidad no importa a otra, así que ir del tablero a las cuentas, o
/// de registrar a abrir una cuenta que falta, lo resuelve este armazón.
class HomeShell extends StatefulWidget {
  const HomeShell({required this.settings, super.key});

  static const Key recordButtonKey = Key('record-movement');

  final ServerSettings settings;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _tab = 0;

  void _openAccounts() => Navigator.of(context)
      .push(MaterialPageRoute<void>(builder: (_) => const AccountsScreen()));

  void _record() => Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => RecordMovementScreen(onOpenAccounts: _openAccounts),
    ),
  );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: <Widget>[
            // Los pendientes se ven siempre, en todas las pestañas, con su
            // número: lo que no llegó al servidor no cuenta en ninguna cifra.
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: PendingStatusView(),
            ),
            Expanded(
              child: IndexedStack(
                index: _tab,
                children: <Widget>[
                  DashboardScreen(onOpenAccounts: _openAccounts),
                  const PeopleScreen(),
                  const BudgetsScreen(),
                  _SettingsTab(
                    origin: widget.settings.address.origin,
                    onOpenAccounts: _openAccounts,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: _tab == 0
          ? FloatingActionButton.extended(
              key: HomeShell.recordButtonKey,
              onPressed: _record,
              icon: const Icon(Icons.add),
              label: const Text('Registrar'),
            )
          : null,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (int tab) => setState(() => _tab = tab),
        destinations: const <NavigationDestination>[
          NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            label: 'Tablero',
          ),
          NavigationDestination(
            icon: Icon(Icons.people_outline),
            label: 'Personas',
          ),
          NavigationDestination(
            icon: Icon(Icons.savings_outlined),
            label: 'Presupuestos',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            label: 'Ajustes',
          ),
        ],
      ),
    );
  }
}

class _SettingsTab extends StatelessWidget {
  const _SettingsTab({required this.origin, required this.onOpenAccounts});

  final String origin;
  final VoidCallback onOpenAccounts;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ajustes')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
        children: <Widget>[
          SectionCard(
            title: 'Servidor',
            child: ConnectionStatusView(origin: origin),
          ),
          const SizedBox(height: 12),
          Card(
            clipBehavior: Clip.antiAlias,
            child: Column(
              children: <Widget>[
                ListTile(
                  leading: const Icon(Icons.account_balance_wallet_outlined),
                  title: const Text('Cuentas y categorías'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: onOpenAccounts,
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.description_outlined),
                  title: const Text('Licencias'),
                  trailing: const Icon(Icons.chevron_right),
                  // Las de los paquetes y la de la fuente (Inter, OFL).
                  onTap: () => showLicensePage(
                    context: context,
                    applicationName: 'FinanceApp',
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

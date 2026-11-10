import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/forms/submit_state.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/reading/reading.dart';
import '../../../core/text/text_input.dart';
import '../../../core/widgets/reading_notices.dart';
import 'accounts_view_model.dart';

/// Las cuentas y categorías, agrupadas por tipo, y abrir una nueva.
class AccountsScreen extends ConsumerWidget {
  const AccountsScreen({super.key});

  static const Key openButtonKey = Key('open-account');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<Reading<List<AccountRef>>?> accounts = ref.watch(
      accountsViewModelProvider,
    );
    return Scaffold(
      appBar: AppBar(title: const Text('Cuentas y categorías')),
      floatingActionButton: FloatingActionButton.extended(
        key: openButtonKey,
        onPressed: () => showModalBottomSheet<void>(
          context: context,
          isScrollControlled: true,
          builder: (_) => const OpenAccountSheet(),
        ),
        icon: const Icon(Icons.add),
        label: const Text('Abrir'),
      ),
      body: switch (accounts) {
        AsyncData<Reading<List<AccountRef>>?>(
          value: final Reading<List<AccountRef>> r,
        ) =>
          ListView(
            padding: const EdgeInsets.only(bottom: 96),
            children: <Widget>[
              StaleNotice(reading: r),
              if (r.value.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(24),
                  child: Text(
                    'Todavía no hay cuentas. Abre las cuentas donde tienes '
                    'plata, las categorías de gasto e ingreso y una de '
                    'patrimonio para los saldos iniciales.',
                  ),
                ),
              for (final AccountKind kind in AccountKind.values)
                ..._group(context, kind, r.value),
            ],
          ),
        AsyncError<Reading<List<AccountRef>>?>(:final Object error) =>
          FailureNotice(
            failure: error,
            onRetry: () =>
                ref.read(accountsViewModelProvider.notifier).refresh(),
          ),
        _ => const Center(child: CircularProgressIndicator()),
      },
    );
  }

  static List<Widget> _group(
    BuildContext context,
    AccountKind kind,
    List<AccountRef> all,
  ) {
    final List<AccountRef> ofKind = all
        .where((AccountRef a) => a.kind == kind && !a.isArchived)
        .toList();
    if (ofKind.isEmpty) {
      return const <Widget>[];
    }
    return <Widget>[
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
        child: Text(kind.label, style: Theme.of(context).textTheme.titleSmall),
      ),
      for (final AccountRef account in ofKind)
        ListTile(dense: true, title: Text(account.name)),
    ];
  }
}

/// Abrir una cuenta: el nombre y el tipo. Se escribe en línea; sin conexión,
/// se dice y no queda nada guardado.
class OpenAccountSheet extends ConsumerStatefulWidget {
  const OpenAccountSheet({this.initialKind = AccountKind.asset, super.key});

  static const Key nameFieldKey = Key('account-name');
  static const Key submitKey = Key('account-submit');

  final AccountKind initialKind;

  @override
  ConsumerState<OpenAccountSheet> createState() => _OpenAccountSheetState();
}

class _OpenAccountSheetState extends ConsumerState<OpenAccountSheet> {
  final TextEditingController _name = TextEditingController();
  late AccountKind _kind = widget.initialKind;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final SubmitState state = ref.watch(openAccountViewModelProvider);
    ref.listen(openAccountViewModelProvider, (_, SubmitState next) {
      if (next.isDone) {
        Navigator.of(context).pop();
      }
    });
    return _Sheet(
      title: 'Abrir una cuenta',
      children: <Widget>[
        TextField(
          key: OpenAccountSheet.nameFieldKey,
          controller: _name,
          maxLength: maxNameLength,
          textCapitalization: TextCapitalization.sentences,
          decoration: const InputDecoration(labelText: 'Nombre'),
        ),
        DropdownButtonFormField<AccountKind>(
          initialValue: _kind,
          decoration: const InputDecoration(labelText: 'Tipo'),
          items: <DropdownMenuItem<AccountKind>>[
            for (final AccountKind kind in AccountKind.values)
              DropdownMenuItem<AccountKind>(
                value: kind,
                child: Text(kind.label),
              ),
          ],
          onChanged: (AccountKind? kind) =>
              setState(() => _kind = kind ?? _kind),
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
          key: OpenAccountSheet.submitKey,
          onPressed: state.isSubmitting
              ? null
              : () => ref
                    .read(openAccountViewModelProvider.notifier)
                    .submit(name: _name.text, kind: _kind),
          child: Text(state.isSubmitting ? 'Abriendo…' : 'Abrir'),
        ),
      ],
    );
  }
}

class _Sheet extends StatelessWidget {
  const _Sheet({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Padding(
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
        Text(title, style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 16),
        ...children,
      ],
    ),
  );
}

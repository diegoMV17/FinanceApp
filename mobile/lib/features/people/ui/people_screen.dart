import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/forms/submit_state.dart';
import '../../../core/ledger/account_balance.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/person.dart';
import '../../../core/money/money_format.dart';
import '../../../core/reading/reading.dart';
import '../../../core/text/text_input.dart';
import '../../../core/widgets/reading_notices.dart';
import 'people_view_model.dart';

/// Las personas: cuánto me debe cada una, neto, como lo dice el servidor.
class PeopleScreen extends ConsumerWidget {
  const PeopleScreen({super.key});

  static const Key newPersonKey = Key('new-person');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<Reading<List<PersonPosition>>?> people = ref.watch(
      peopleViewModelProvider,
    );
    return Scaffold(
      appBar: AppBar(title: const Text('Personas')),
      floatingActionButton: FloatingActionButton.extended(
        key: newPersonKey,
        onPressed: () => showModalBottomSheet<void>(
          context: context,
          isScrollControlled: true,
          builder: (_) => const _NewPersonSheet(),
        ),
        icon: const Icon(Icons.person_add_alt),
        label: const Text('Nueva'),
      ),
      body: switch (people) {
        AsyncData<Reading<List<PersonPosition>>?>(
          value: final Reading<List<PersonPosition>> r,
        ) =>
          RefreshIndicator(
            onRefresh: () async {
              ref.read(peopleViewModelProvider.notifier).refresh();
              await ref.read(peopleViewModelProvider.future);
            },
            child: ListView(
              padding: const EdgeInsets.only(bottom: 96),
              children: <Widget>[
                StaleNotice(reading: r),
                if (r.value.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(24),
                    child: Text('Todavía no hay personas.'),
                  ),
                for (final PersonPosition person in r.value.where(
                  (PersonPosition p) => !p.isArchived,
                ))
                  ListTile(
                    title: Text(person.name),
                    subtitle: Text(_netLabel(person)),
                    trailing: Text(person.netOwedToYou.inPesos),
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => PersonScreen(personId: person.id),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        AsyncError<Reading<List<PersonPosition>>?>(:final Object error) =>
          FailureNotice(
            failure: error,
            onRetry: () => ref.read(peopleViewModelProvider.notifier).refresh(),
          ),
        _ => const Center(child: CircularProgressIndicator()),
      },
    );
  }

  /// Lo que ya decidió el servidor, dicho en palabras: el signo lo trae el
  /// neto, la app no lo interpreta más allá de leerlo.
  static String _netLabel(PersonPosition person) {
    if (person.netOwedToYou.isPositive) {
      return 'Te debe, neto';
    }
    if (person.netOwedToYou.isNegative) {
      return 'Le debes, neto';
    }
    return 'A paz y salvo';
  }
}

/// Una persona: lo que me debe, lo que le debo, su plata que guardo, y las
/// cuentas que lo explican.
class PersonScreen extends ConsumerWidget {
  const PersonScreen({required this.personId, super.key});

  static const Key linkKey = Key('link-account');

  final String personId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final Reading<List<PersonPosition>>? reading = ref
        .watch(peopleViewModelProvider)
        .value;
    final PersonPosition? person = reading?.value
        .where((PersonPosition p) => p.id == personId)
        .firstOrNull;
    if (reading == null || person == null) {
      return Scaffold(
        appBar: AppBar(),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text(person.name)),
      floatingActionButton: FloatingActionButton.extended(
        key: linkKey,
        onPressed: () => showModalBottomSheet<void>(
          context: context,
          isScrollControlled: true,
          builder: (_) => _PersonAccountSheet(personId: personId),
        ),
        icon: const Icon(Icons.link),
        label: const Text('Cuenta'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          StaleNotice(reading: reading),
          _Line('Me debe', person.owedToYou.inPesos),
          _Line('Le debo', person.youOwe.inPesos),
          _Line('Su plata que guardo', person.theirFund.inPesos),
          const Divider(),
          _Line('Neto a mi favor', person.netOwedToYou.inPesos),
          const SizedBox(height: 16),
          Text('Cuentas', style: Theme.of(context).textTheme.titleMedium),
          if (person.accounts.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: Text('Sin cuentas vinculadas.'),
            ),
          for (final AccountBalance line in person.accounts)
            ListTile(
              title: Text(line.account.name),
              subtitle: Text(line.account.kind.label),
              trailing: Text(line.balance.inPesos),
            ),
        ],
      ),
    );
  }
}

class _Line extends StatelessWidget {
  const _Line(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(label),
    trailing: Text(value, style: Theme.of(context).textTheme.titleMedium),
  );
}

class _NewPersonSheet extends ConsumerStatefulWidget {
  const _NewPersonSheet();

  @override
  ConsumerState<_NewPersonSheet> createState() => _NewPersonSheetState();
}

class _NewPersonSheetState extends ConsumerState<_NewPersonSheet> {
  final TextEditingController _name = TextEditingController();

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final SubmitState state = ref.watch(newPersonViewModelProvider);
    ref.listen(newPersonViewModelProvider, (_, SubmitState next) {
      if (next.isDone) {
        Navigator.of(context).pop();
      }
    });
    return _Sheet(
      title: 'Nueva persona',
      error: state.error,
      children: <Widget>[
        TextField(
          controller: _name,
          maxLength: maxNameLength,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(labelText: 'Nombre'),
        ),
        FilledButton(
          onPressed: state.isSubmitting
              ? null
              : () => ref
                    .read(newPersonViewModelProvider.notifier)
                    .submit(_name.text),
          child: const Text('Registrar'),
        ),
      ],
    );
  }
}

/// Vincular una cuenta que ya existe, o abrir una nueva para esta persona.
class _PersonAccountSheet extends ConsumerStatefulWidget {
  const _PersonAccountSheet({required this.personId});

  final String personId;

  @override
  ConsumerState<_PersonAccountSheet> createState() =>
      _PersonAccountSheetState();
}

class _PersonAccountSheetState extends ConsumerState<_PersonAccountSheet> {
  final TextEditingController _name = TextEditingController();
  String? _existing;
  AccountKind _kind = AccountKind.receivable;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final SubmitState state = ref.watch(
      personAccountViewModelProvider(widget.personId),
    );
    final List<AccountRef> linkable =
        ref.watch(linkableAccountsProvider).value?.value ?? <AccountRef>[];
    ref.listen(personAccountViewModelProvider(widget.personId), (
      _,
      SubmitState next,
    ) {
      if (next.isDone) {
        Navigator.of(context).pop();
      }
    });
    final PersonAccountViewModel viewModel = ref.read(
      personAccountViewModelProvider(widget.personId).notifier,
    );
    return _Sheet(
      title: 'Cuenta de esta persona',
      error: state.error,
      children: <Widget>[
        if (linkable.isNotEmpty) ...<Widget>[
          DropdownButtonFormField<String>(
            initialValue: _existing,
            decoration: const InputDecoration(labelText: 'Una que ya existe'),
            items: <DropdownMenuItem<String>>[
              for (final AccountRef a in linkable)
                DropdownMenuItem<String>(
                  value: a.id,
                  child: Text('${a.name} · ${a.kind.label}'),
                ),
            ],
            onChanged: (String? id) => setState(() => _existing = id),
          ),
          FilledButton.tonal(
            onPressed: state.isSubmitting || _existing == null
                ? null
                : () => viewModel.linkExisting(_existing!),
            child: const Text('Vincular'),
          ),
          const Divider(height: 32),
        ],
        TextField(
          controller: _name,
          maxLength: maxNameLength,
          textCapitalization: TextCapitalization.sentences,
          decoration: const InputDecoration(labelText: 'O una nueva: nombre'),
        ),
        DropdownButtonFormField<AccountKind>(
          initialValue: _kind,
          decoration: const InputDecoration(labelText: 'Tipo'),
          items: <DropdownMenuItem<AccountKind>>[
            for (final AccountKind kind in AccountKind.values.where(
              (AccountKind k) => k.belongsToAPerson,
            ))
              DropdownMenuItem<AccountKind>(
                value: kind,
                child: Text(kind.label),
              ),
          ],
          onChanged: (AccountKind? kind) =>
              setState(() => _kind = kind ?? _kind),
        ),
        FilledButton(
          onPressed: state.isSubmitting
              ? null
              : () => viewModel.openNew(name: _name.text, kind: _kind),
          child: const Text('Abrir y vincular'),
        ),
      ],
    );
  }
}

class _Sheet extends StatelessWidget {
  const _Sheet({required this.title, required this.children, this.error});

  final String title;
  final String? error;
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
        if (error != null)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text(
              error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ),
      ],
    ),
  );
}

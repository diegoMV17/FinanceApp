import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/forms/submit_state.dart';
import '../../../core/forms/write_once.dart';
import '../../../core/ids/uuid.dart';
import '../../../core/ledger/account_draft.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/book_changes.dart';
import '../../../core/ledger/person.dart';
import '../../../core/lock/app_lock.dart';
import '../../../core/reading/reading.dart';
import '../../../core/text/text_input.dart';
import '../data/people_repository.dart';

/// Las personas con su posición. `null` mientras la app está bloqueada.
class PeopleViewModel extends AsyncNotifier<Reading<List<PersonPosition>>?> {
  @override
  Future<Reading<List<PersonPosition>>?> build() async {
    final bool open = ref.watch(
      appLockProvider.select((AppLockState s) => s.isOpen),
    );
    ref.watch(bookChangesProvider);
    if (!open) {
      return null;
    }
    return (await ref.watch(peopleRepositoryProvider.future)).people();
  }

  void refresh() => ref.invalidateSelf();
}

final AsyncNotifierProvider<PeopleViewModel, Reading<List<PersonPosition>>?>
peopleViewModelProvider =
    AsyncNotifierProvider<PeopleViewModel, Reading<List<PersonPosition>>?>(
      PeopleViewModel.new,
    );

/// Registrar a alguien. El id, una vez por formulario.
class NewPersonViewModel extends Notifier<SubmitState> {
  final String _id = generateUuidV4();

  @override
  SubmitState build() => const SubmitState.editing();

  Future<void> submit(String name) async {
    if (state.isSubmitting || state.isDone) {
      return;
    }
    final PersonDraft draft;
    try {
      draft = PersonDraft(id: _id, name: name);
    } on TextInputException catch (invalid) {
      state = SubmitState.editing(
        textMessage(invalid.problem, max: maxNameLength),
      );
      return;
    }
    state = const SubmitState.submitting();
    final SubmitState result = await writeOnce(
      () async => (await ref.read(peopleRepositoryProvider.future)).open(draft),
    );
    _finish(ref, result, (SubmitState s) => state = s);
  }
}

final NotifierProvider<NewPersonViewModel, SubmitState>
newPersonViewModelProvider =
    NotifierProvider.autoDispose<NewPersonViewModel, SubmitState>(
      NewPersonViewModel.new,
    );

/// Vincular una cuenta a una persona: una que ya existe, o una nueva que se
/// abre y se vincula. La nueva lleva su id, una vez por formulario.
class PersonAccountViewModel extends Notifier<SubmitState> {
  PersonAccountViewModel(this.personId);

  final String personId;
  final String _newAccountId = generateUuidV4();

  @override
  SubmitState build() => const SubmitState.editing();

  Future<void> linkExisting(String accountId) async {
    if (state.isSubmitting || state.isDone) {
      return;
    }
    state = const SubmitState.submitting();
    final SubmitState result = await writeOnce(
      () async =>
          (await ref.read(peopleRepositoryProvider.future))
              .link(personId: personId, accountId: accountId),
    );
    _finish(ref, result, (SubmitState s) => state = s);
  }

  Future<void> openNew({
    required String name,
    required AccountKind kind,
  }) async {
    if (state.isSubmitting || state.isDone) {
      return;
    }
    final AccountDraft draft;
    try {
      draft = AccountDraft(id: _newAccountId, name: name, kind: kind);
    } on TextInputException catch (invalid) {
      state = SubmitState.editing(
        textMessage(invalid.problem, max: maxNameLength),
      );
      return;
    }
    state = const SubmitState.submitting();
    final SubmitState result = await writeOnce(
      () async =>
          (await ref.read(peopleRepositoryProvider.future))
              .openAccountFor(personId: personId, account: draft),
    );
    _finish(ref, result, (SubmitState s) => state = s);
  }
}

final personAccountViewModelProvider = NotifierProvider.autoDispose
    .family<PersonAccountViewModel, SubmitState, String>(
      PersonAccountViewModel.new,
    );

/// Las cuentas que se pueden vincular a alguien.
final FutureProvider<Reading<List<AccountRef>>> linkableAccountsProvider =
    FutureProvider.autoDispose<Reading<List<AccountRef>>>(
      (Ref ref) async =>
          (await ref.watch(peopleRepositoryProvider.future)).linkableAccounts(),
    );

void _finish(Ref ref, SubmitState result, void Function(SubmitState) set) {
  if (!ref.mounted) {
    return;
  }
  if (result.isDone) {
    ref.read(bookChangesProvider.notifier).changed();
  }
  set(result);
}

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/forms/submit_state.dart';
import '../../../core/forms/write_once.dart';
import '../../../core/ids/uuid.dart';
import '../../../core/ledger/account_draft.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/book_changes.dart';
import '../../../core/lock/app_lock.dart';
import '../../../core/reading/reading.dart';
import '../../../core/text/text_input.dart';
import '../data/accounts_repository.dart';

/// Las cuentas que hay. `null` mientras la app está bloqueada: no se lee nada
/// hasta desbloquear, y cada desbloqueo o cambio del libro vuelve a pedir.
class AccountsViewModel extends AsyncNotifier<Reading<List<AccountRef>>?> {
  @override
  Future<Reading<List<AccountRef>>?> build() async {
    final bool open = ref.watch(
      appLockProvider.select((AppLockState s) => s.isOpen),
    );
    ref.watch(bookChangesProvider);
    if (!open) {
      return null;
    }
    return (await ref.watch(accountsRepositoryProvider.future)).accounts();
  }

  void refresh() => ref.invalidateSelf();
}

final AsyncNotifierProvider<AccountsViewModel, Reading<List<AccountRef>>?>
accountsViewModelProvider =
    AsyncNotifierProvider<AccountsViewModel, Reading<List<AccountRef>>?>(
      AccountsViewModel.new,
    );

/// Abrir una cuenta. El id se genera una vez por formulario: reintentar
/// después de un fallo manda el mismo, y si la primera vez sí llegó, el
/// servidor responde 200 en vez de abrir otra.
class OpenAccountViewModel extends Notifier<SubmitState> {
  final String _id = generateUuidV4();

  @override
  SubmitState build() => const SubmitState.editing();

  Future<void> submit({required String name, required AccountKind kind}) async {
    if (state.isSubmitting || state.isDone) {
      return;
    }
    final AccountDraft draft;
    try {
      draft = AccountDraft(id: _id, name: name, kind: kind);
    } on TextInputException catch (invalid) {
      state = SubmitState.editing(
        textMessage(invalid.problem, max: maxNameLength),
      );
      return;
    }
    state = const SubmitState.submitting();
    final SubmitState result = await writeOnce(
      () async =>
          (await ref.read(accountsRepositoryProvider.future)).open(draft),
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

final NotifierProvider<OpenAccountViewModel, SubmitState>
openAccountViewModelProvider =
    NotifierProvider.autoDispose<OpenAccountViewModel, SubmitState>(
      OpenAccountViewModel.new,
    );

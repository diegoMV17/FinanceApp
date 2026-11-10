import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/ledger/account_draft.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/person.dart';
import '../../../core/reading/reading.dart';
import '../../../data/catalog/account_catalog.dart';
import '../../../data/remote/accounts_service.dart';
import '../../../data/remote/people_service.dart';

/// Las personas: la lista con su posición, y registrar o vincular en línea.
///
/// Las escrituras no pasan por la cola (solo los movimientos lo hacen): si no
/// hay conexión, se dice y no queda nada guardado. El id lo trae el
/// borrador, generado una vez por formulario, así que reintentar después de
/// un tiempo de espera responde 200 y no duplica a nadie.
class PeopleRepository {
  const PeopleRepository(this._people, this._accounts, this._catalog);

  final PeopleService _people;
  final AccountsService _accounts;
  final AccountCatalog _catalog;

  Future<Reading<List<PersonPosition>>> people() => _people.people();

  Future<void> open(PersonDraft person) => _people.open(person);

  Future<void> link({required String personId, required String accountId}) =>
      _people.link(personId: personId, accountId: accountId);

  /// Abre la cuenta y la vincula. Son dos escrituras, cada una con su id: si
  /// la segunda falla, reintentar con el mismo borrador hace que la primera
  /// responda 200 (ya estaba) y la segunda siga.
  Future<void> openAccountFor({
    required String personId,
    required AccountDraft account,
  }) async {
    await _accounts.open(account);
    await _people.link(personId: personId, accountId: account.id);
  }

  /// Las que se pueden vincular: de alguien (lo que me deben, lo que debo,
  /// la plata de otro) y abiertas.
  Future<Reading<List<AccountRef>>> linkableAccounts() async {
    final Reading<List<AccountRef>> all = await _catalog.accounts();
    return Reading<List<AccountRef>>(
      all.value
          .where((AccountRef a) => a.kind.belongsToAPerson && !a.isArchived)
          .toList(),
      all.readAt,
      all.staleBecause,
    );
  }
}

final FutureProvider<PeopleRepository> peopleRepositoryProvider =
    FutureProvider<PeopleRepository>(
      (Ref ref) async => PeopleRepository(
        await ref.watch(peopleServiceProvider.future),
        await ref.watch(accountsServiceProvider.future),
        await ref.watch(accountCatalogProvider.future),
      ),
    );

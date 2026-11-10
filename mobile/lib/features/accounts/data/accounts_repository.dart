import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/ledger/account_draft.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/reading/reading.dart';
import '../../../data/catalog/account_catalog.dart';
import '../../../data/remote/accounts_service.dart';

/// Las cuentas: cuáles hay y abrir una nueva, en línea.
class AccountsRepository {
  const AccountsRepository(this._accounts, this._catalog);

  final AccountsService _accounts;
  final AccountCatalog _catalog;

  Future<Reading<List<AccountRef>>> accounts() => _catalog.accounts();

  Future<AccountRef> open(AccountDraft account) => _accounts.open(account);
}

final FutureProvider<AccountsRepository> accountsRepositoryProvider =
    FutureProvider<AccountsRepository>(
      (Ref ref) async => AccountsRepository(
        await ref.watch(accountsServiceProvider.future),
        await ref.watch(accountCatalogProvider.future),
      ),
    );

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/ledger/account_draft.dart';
import '../../core/ledger/account_ref.dart';
import '../../core/network/server_gateway.dart';
import '../api/export.dart';
import 'answers.dart';

/// Las escrituras de cuentas, en línea. Solo habla y traduce.
///
/// Aquí y no en una funcionalidad porque abrir una cuenta lo usan dos:
/// cuentas, y personas (abrir la cuenta de alguien para vincularla).
class AccountsService {
  const AccountsService(this._gateway);

  final ServerGateway _gateway;

  /// Un 200 (ya estaba) también es éxito: es el mismo borrador reintentado.
  Future<AccountRef> open(AccountDraft account) async {
    final AccountResponse answer = await _gateway.call(
      (FinanceApi api) => api.accounts.accountsOpen(
        body: OpenAccountRequest(
          id: account.id,
          name: account.name,
          type: AccountType.fromJson(account.kind.wire),
        ),
      ),
    );
    requireAnswerTo(answer.id, account.id);
    return AccountRef(id: answer.id, name: answer.name, kind: account.kind);
  }
}

final FutureProvider<AccountsService> accountsServiceProvider =
    FutureProvider<AccountsService>(
      (Ref ref) async =>
          AccountsService(await ref.watch(serverGatewayProvider.future)),
    );

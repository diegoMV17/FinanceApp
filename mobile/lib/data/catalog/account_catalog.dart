import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/ledger/account_ref.dart';
import '../api/export.dart';
import '../reading/read_through.dart';

/// La copia de `GET /api/balances`. La comparten el catálogo y el tablero:
/// es la misma lectura.
const String balancesKey = 'balances';

/// Las cuentas que ofrecen los selectores: las de plata para pagar, las
/// categorías para clasificar, las de una persona para vincular.
///
/// Sale de `GET /api/balances` porque el contrato no tiene una lista de
/// cuentas aparte, y esa lectura las trae todas, archivadas incluidas. El
/// saldo se descarta: aquí solo se elige.
class AccountCatalog {
  const AccountCatalog(this._reader);

  final ReadThrough _reader;

  Future<Reading<List<AccountRef>>> accounts() =>
      _reader.read<BalancesResponse, List<AccountRef>>(
        key: balancesKey,
        fetch: (FinanceApi api) => api.book.bookBalances(),
        toJson: (BalancesResponse response) => response.toJson(),
        fromJson: BalancesResponse.fromJson,
        translate: (BalancesResponse response) => <AccountRef>[
          for (final BalanceResponse account in response.accounts)
            accountRefOf(account),
        ],
      );
}

/// Una cuenta del contrato como la ven las pantallas. Un tipo desconocido
/// lanza [FormatException]: no se ofrece como si fuera otro.
AccountRef accountRefOf(BalanceResponse account) => AccountRef(
  id: account.id,
  name: account.name,
  kind: AccountKind.fromWire(account.type.json ?? ''),
  isArchived: account.archivedAt != null,
);

final FutureProvider<AccountCatalog> accountCatalogProvider =
    FutureProvider<AccountCatalog>(
      (Ref ref) async =>
          AccountCatalog(await ref.watch(readThroughProvider.future)),
    );

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/ledger/person.dart';
import '../../core/money/money.dart';
import '../../core/network/server_gateway.dart';
import '../../core/reading/reading.dart';
import '../api/export.dart';
import '../catalog/account_catalog.dart';
import '../reading/read_through.dart';
import 'answers.dart';

/// Las personas en el servidor: la lectura con su última copia, y las
/// escrituras en línea. Solo habla y traduce.
class PeopleService {
  const PeopleService(this._gateway, this._reader);

  final ServerGateway _gateway;
  final ReadThrough _reader;

  Future<Reading<List<PersonPosition>>> people() =>
      _reader.read<PeopleResponse, List<PersonPosition>>(
        key: 'people',
        fetch: (FinanceApi api) => api.people.peoplePeople(),
        toJson: (PeopleResponse r) => r.toJson(),
        fromJson: PeopleResponse.fromJson,
        translate: (PeopleResponse r) => r.people.map(_positionOf).toList(),
      );

  Future<void> open(PersonDraft person) async {
    final PersonResponse answer = await _gateway.call(
      (FinanceApi api) => api.people.peopleOpen(
        body: OpenPersonRequest(id: person.id, name: person.name),
      ),
    );
    requireAnswerTo(answer.id, person.id);
  }

  Future<void> link({
    required String personId,
    required String accountId,
  }) async {
    final AccountResponse answer = await _gateway.call(
      (FinanceApi api) => api.people.peopleLink(
        id: personId,
        body: LinkAccountRequest(account: accountId),
      ),
    );
    requireAnswerTo(answer.id, accountId);
  }

  static PersonPosition _positionOf(PersonPositionResponse p) => PersonPosition(
    id: p.id,
    name: p.name,
    isArchived: p.archivedAt != null,
    owedToYou: Money.ofCents(p.owedToYouInCents),
    youOwe: Money.ofCents(p.youOweInCents),
    theirFund: Money.ofCents(p.theirFundInCents),
    netOwedToYou: Money.ofCents(p.netOwedToYouInCents),
    accounts: p.accounts.map(accountBalanceOf).toList(),
  );
}

final FutureProvider<PeopleService> peopleServiceProvider =
    FutureProvider<PeopleService>(
      (Ref ref) async => PeopleService(
        await ref.watch(serverGatewayProvider.future),
        await ref.watch(readThroughProvider.future),
      ),
    );

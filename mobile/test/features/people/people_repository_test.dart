import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/ledger/account_draft.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/person.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/data/catalog/account_catalog.dart';
import 'package:mobile/data/remote/accounts_service.dart';
import 'package:mobile/data/remote/people_service.dart';
import 'package:mobile/features/people/data/people_repository.dart';

import '../../support/server_fixture.dart';

void main() {
  const String juan = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String loan = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';

  late ServerFixture server;
  late PeopleRepository repository;

  Map<String, Object?> account() => <String, Object?>{
    'id': loan,
    'name': 'Préstamo a Juan',
    'type': 'RECEIVABLE',
    'balanceInCents': 10000000,
  };

  setUp(() {
    server = ServerFixture();
    repository = PeopleRepository(
      PeopleService(server.gateway, server.reader),
      AccountsService(server.gateway),
      AccountCatalog(server.reader),
    );
  });

  tearDown(() => server.close());

  test('cada persona con lo que dice el servidor, tal cual', () async {
    server.answer('GET /api/people', 200, <String, Object?>{
      'asOf': '2026-11-09',
      'people': <Object?>[
        <String, Object?>{
          'id': juan,
          'name': 'Juan',
          'owedToYouInCents': 10000000,
          'youOweInCents': 3000000,
          'theirFundInCents': 5000000,
          'netOwedToYouInCents': 7000000,
          'accounts': <Object?>[account()],
        },
      ],
    });

    final Reading<List<PersonPosition>> reading = await repository.people();
    final PersonPosition person = reading.value.single;

    expect(person.name, 'Juan');
    expect(person.isArchived, isFalse);
    expect(person.owedToYou, Money.ofCents(10000000));
    expect(person.youOwe, Money.ofCents(3000000));
    expect(person.theirFund, Money.ofCents(5000000));
    expect(person.netOwedToYou, Money.ofCents(7000000));
    expect(person.accounts.single.account.kind, AccountKind.receivable);
    expect(person.accounts.single.balance, Money.ofCents(10000000));
  });

  test('registrar a alguien manda el id del teléfono y su nombre', () async {
    server.answer('POST /api/people', 201, <String, Object?>{
      'id': juan,
      'name': 'Juan',
    });

    await repository.open(PersonDraft(id: juan, name: ' Juan '));

    expect(server.sent('POST /api/people').single, <String, Object?>{
      'id': juan,
      'name': 'Juan',
    });
  });

  test('un 200 (ya estaba) también es éxito: fue un reintento', () async {
    server.answer('POST /api/people', 200, <String, Object?>{
      'id': juan,
      'name': 'Juan',
    });

    await repository.open(PersonDraft(id: juan, name: 'Juan'));
  });

  test('un nombre repetido vuelve como el rechazo del servidor', () async {
    server.answer(
      'POST /api/people',
      409,
      apiProblem('duplicate-person-name', 409),
    );

    await expectLater(
      repository.open(PersonDraft(id: juan, name: 'Juan')),
      throwsA(
        const RequestFailure.refused('urn:finance:error:duplicate-person-name'),
      ),
    );
  });

  test('una respuesta que no es de esta persona no se da por buena', () async {
    server.answer('POST /api/people', 200, <String, Object?>{
      'id': loan,
      'name': 'Otro',
    });

    await expectLater(
      repository.open(PersonDraft(id: juan, name: 'Juan')),
      throwsA(const RequestFailure(FailureKind.unexpected)),
    );
  });

  test('vincular una cuenta existente', () async {
    server.answer('POST /api/people/$juan/accounts', 201, <String, Object?>{
      'id': loan,
      'name': 'Préstamo a Juan',
      'type': 'RECEIVABLE',
    });

    await repository.link(personId: juan, accountId: loan);

    expect(
      server.sent('POST /api/people/$juan/accounts').single,
      <String, Object?>{'account': loan},
    );
  });

  test(
    'abrir la cuenta de alguien: se abre y se vincula, en ese orden',
    () async {
      server
        ..answer('POST /api/accounts', 201, <String, Object?>{
          'id': loan,
          'name': 'Préstamo a Juan',
          'type': 'RECEIVABLE',
        })
        ..answer('POST /api/people/$juan/accounts', 201, <String, Object?>{
          'id': loan,
          'name': 'Préstamo a Juan',
          'type': 'RECEIVABLE',
        });

      await repository.openAccountFor(
        personId: juan,
        account: AccountDraft(
          id: loan,
          name: 'Préstamo a Juan',
          kind: AccountKind.receivable,
        ),
      );

      expect(
        server.adapter.requests.map((r) => '${r.method} ${r.uri.path}'),
        <String>['POST /api/accounts', 'POST /api/people/$juan/accounts'],
      );
      expect(server.sent('POST /api/accounts').single, <String, Object?>{
        'id': loan,
        'name': 'Préstamo a Juan',
        'type': 'RECEIVABLE',
      });
    },
  );

  test('si abrir falla, no se intenta vincular', () async {
    server.answer(
      'POST /api/accounts',
      409,
      apiProblem('duplicate-account-name', 409),
    );

    await expectLater(
      repository.openAccountFor(
        personId: juan,
        account: AccountDraft(
          id: loan,
          name: 'Préstamo a Juan',
          kind: AccountKind.receivable,
        ),
      ),
      throwsA(isA<RequestFailure>()),
    );
    expect(server.sent('POST /api/people/$juan/accounts'), isEmpty);
  });

  test('las cuentas que se pueden vincular: de alguien y abiertas', () async {
    server.answer('GET /api/balances', 200, <String, Object?>{
      'asOf': '2026-11-09',
      'accounts': <Object?>[
        account(),
        <String, Object?>{
          'id': '0a3c0f5e-2b1d-4c8e-9f00-000000000003',
          'name': 'Nequi',
          'type': 'ASSET',
          'balanceInCents': 0,
        },
        <String, Object?>{
          'id': '0a3c0f5e-2b1d-4c8e-9f00-000000000004',
          'name': 'Viejo',
          'type': 'PAYABLE',
          'balanceInCents': 0,
          'archivedAt': '2026-01-01T00:00:00Z',
        },
      ],
    });

    final List<AccountRef> linkable =
        (await repository.linkableAccounts()).value;

    expect(linkable.map((AccountRef a) => a.id), <String>[loan]);
  });
}

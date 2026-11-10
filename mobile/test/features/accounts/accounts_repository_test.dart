import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/ledger/account_draft.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/data/catalog/account_catalog.dart';
import 'package:mobile/data/remote/accounts_service.dart';
import 'package:mobile/features/accounts/data/accounts_repository.dart';

import '../../support/server_fixture.dart';

void main() {
  const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';

  late ServerFixture server;
  late AccountsRepository repository;

  AccountDraft draft(AccountKind kind) =>
      AccountDraft(id: id, name: ' Alimentación ', kind: kind);

  setUp(() {
    server = ServerFixture();
    repository = AccountsRepository(
      AccountsService(server.gateway),
      AccountCatalog(server.reader),
    );
  });

  tearDown(() => server.close());

  test('abrir manda el id del teléfono, el nombre y el tipo', () async {
    server.answer('POST /api/accounts', 201, <String, Object?>{
      'id': id,
      'name': 'Alimentación',
      'type': 'EXPENSE',
    });

    final AccountRef opened = await repository.open(draft(AccountKind.expense));

    expect(server.sent('POST /api/accounts').single, <String, Object?>{
      'id': id,
      'name': 'Alimentación',
      'type': 'EXPENSE',
    });
    expect(opened.kind, AccountKind.expense);
  });

  test('cada tipo viaja con su nombre del contrato', () async {
    for (final AccountKind kind in AccountKind.values) {
      server.answer('POST /api/accounts', 201, <String, Object?>{
        'id': id,
        'name': 'Alimentación',
        'type': kind.wire,
      });

      await repository.open(draft(kind));

      expect(
        (server.sent('POST /api/accounts').last!
            as Map<String, Object?>)['type'],
        kind.wire,
      );
    }
  });

  test('un 200 (ya estaba) es éxito: fue un reintento del mismo', () async {
    server.answer('POST /api/accounts', 200, <String, Object?>{
      'id': id,
      'name': 'Alimentación',
      'type': 'EXPENSE',
    });

    await repository.open(draft(AccountKind.expense));
  });

  test('un nombre repetido vuelve como el rechazo del servidor', () async {
    server.answer(
      'POST /api/accounts',
      409,
      apiProblem('duplicate-account-name', 409),
    );

    await expectLater(
      repository.open(draft(AccountKind.expense)),
      throwsA(
        const RequestFailure.refused(
          'urn:finance:error:duplicate-account-name',
        ),
      ),
    );
  });

  test('una respuesta con otro id no abrió esta cuenta', () async {
    server.answer('POST /api/accounts', 201, <String, Object?>{
      'id': '0a3c0f5e-2b1d-4c8e-9f00-000000000009',
      'name': 'Otra',
      'type': 'EXPENSE',
    });

    await expectLater(
      repository.open(draft(AccountKind.expense)),
      throwsA(const RequestFailure(FailureKind.unexpected)),
    );
  });

  test('sin conexión no queda nada guardado: se dice', () async {
    server.offline = true;

    await expectLater(
      repository.open(draft(AccountKind.expense)),
      throwsA(const RequestFailure(FailureKind.noAnswer)),
    );
  });
}

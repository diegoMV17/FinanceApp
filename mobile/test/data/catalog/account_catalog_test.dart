import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/server_gateway.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/data/catalog/account_catalog.dart';
import 'package:mobile/data/local/local_database.dart';
import 'package:mobile/data/local/read_snapshots_store.dart';
import 'package:mobile/data/reading/read_through.dart';

import '../../support/recording_adapter.dart';

/// Las cuentas que ofrecen los selectores (registrar, presupuestos,
/// personas), sacadas de `GET /api/balances`.
void main() {
  final ServerSettings settings = ServerSettings(
    address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
    token: ApiToken(
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    ),
  );
  const String nequi = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String food = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';

  late RecordingAdapter adapter;
  late LocalDatabase db;
  late AccountCatalog catalog;

  Map<String, Object?> account(
    String id,
    String name,
    String type, {
    String? archivedAt,
  }) => <String, Object?>{
    'id': id,
    'name': name,
    'type': type,
    'balanceInCents': 0,
    'archivedAt': ?archivedAt,
  };

  setUp(() {
    adapter = RecordingAdapter();
    db = LocalDatabase(NativeDatabase.memory());
    catalog = AccountCatalog(
      ReadThrough(
        ServerGateway(
          settings,
          () => true,
          (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
        ),
        ReadSnapshotsStore(db),
      ),
    );
  });

  tearDown(() => db.close());

  test('cada cuenta con su nombre, su tipo y si está archivada', () async {
    adapter.answer(200, <String, Object?>{
      'asOf': '2026-11-09',
      'accounts': <Object?>[
        account(nequi, 'Nequi', 'ASSET'),
        account(
          food,
          'Alimentación',
          'EXPENSE',
          archivedAt: '2026-10-01T00:00:00Z',
        ),
      ],
    });

    final Reading<List<AccountRef>> reading = await catalog.accounts();

    expect(reading.value, <AccountRef>[
      const AccountRef(id: nequi, name: 'Nequi', kind: AccountKind.asset),
      const AccountRef(
        id: food,
        name: 'Alimentación',
        kind: AccountKind.expense,
        isArchived: true,
      ),
    ]);
    expect(adapter.last.uri.path, '/api/balances');
  });

  test(
    'un tipo que la app no conoce no se ofrece como si fuera otro',
    () async {
      adapter.answer(200, <String, Object?>{
        'asOf': '2026-11-09',
        'accounts': <Object?>[account(nequi, 'Nequi', 'SAVINGS')],
      });

      await expectLater(
        catalog.accounts(),
        throwsA(const RequestFailure(FailureKind.unexpected)),
      );
    },
  );
}

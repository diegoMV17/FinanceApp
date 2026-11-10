import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/server_gateway.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/data/api/export.dart';
import 'package:mobile/data/local/local_database.dart';
import 'package:mobile/data/local/read_snapshots_store.dart';
import 'package:mobile/data/reading/read_through.dart';

import '../../support/recording_adapter.dart';

/// Leer del servidor y guardar la última copia; sin servidor, mostrar la
/// copia diciendo de cuándo es y por qué no hay algo más nuevo.
void main() {
  final ServerSettings settings = ServerSettings(
    address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
    token: ApiToken(
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    ),
  );
  final DateTime nine = DateTime.utc(2026, 11, 9, 9);
  final DateTime ten = DateTime.utc(2026, 11, 9, 10);

  late RecordingAdapter adapter;
  late LocalDatabase db;
  late ReadSnapshotsStore snapshots;
  late DateTime now;
  late ReadThrough reader;

  Map<String, Object?> balances(String asOf) => <String, Object?>{
    'asOf': asOf,
    'accounts': <Object?>[],
  };

  Future<Reading<String>> readAsOf() => reader.read<BalancesResponse, String>(
    key: 'balances',
    fetch: (FinanceApi api) => api.book.bookBalances(),
    toJson: (BalancesResponse r) => r.toJson(),
    fromJson: BalancesResponse.fromJson,
    translate: (BalancesResponse r) => r.asOf,
  );

  setUp(() {
    adapter = RecordingAdapter();
    db = LocalDatabase(NativeDatabase.memory());
    snapshots = ReadSnapshotsStore(db);
    now = nine;
    reader = ReadThrough(
      ServerGateway(
        settings,
        () => true,
        (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
      ),
      snapshots,
      () => now,
    );
  });

  tearDown(() => db.close());

  test('con servidor: lo de ahora, y queda como copia', () async {
    adapter.answer(200, balances('2026-11-09'));

    final Reading<String> reading = await readAsOf();

    expect(reading.value, '2026-11-09');
    expect(reading.readAt, nine);
    expect(reading.isStale, isFalse);
    expect((await snapshots.read('balances'))!.readAt, nine);
  });

  test('sin servidor: la copia, con su hora y el porqué', () async {
    adapter.answer(200, balances('2026-11-09'));
    await readAsOf();
    now = ten;
    adapter.fail(const SocketException('sin red'));

    final Reading<String> reading = await readAsOf();

    expect(reading.value, '2026-11-09');
    expect(reading.readAt, nine);
    expect(reading.isStale, isTrue);
    expect(reading.staleBecause, const RequestFailure(FailureKind.noAnswer));
  });

  test('sin servidor y sin copia: el fallo, clasificado', () async {
    adapter.fail(const SocketException('sin red'));

    await expectLater(
      readAsOf(),
      throwsA(const RequestFailure(FailureKind.noAnswer)),
    );
  });

  test('con un 401 también se ve la copia, diciendo que es el token', () async {
    adapter.answer(200, balances('2026-11-09'));
    await readAsOf();
    adapter.answer(401, <String, Object?>{
      'type': 'urn:finance:error:unauthorized',
    });

    final Reading<String> reading = await readAsOf();

    expect(
      reading.staleBecause,
      const RequestFailure(FailureKind.unauthorized),
    );
  });

  test('lo que no se puede traducir no se guarda ni se muestra', () async {
    adapter.answer(200, balances('2026-11-09'));
    await readAsOf();
    now = ten;
    adapter.answer(200, balances('2026-11-10'));

    final Reading<String> reading = await reader.read<BalancesResponse, String>(
      key: 'balances',
      fetch: (FinanceApi api) => api.book.bookBalances(),
      toJson: (BalancesResponse r) => r.toJson(),
      fromJson: BalancesResponse.fromJson,
      translate: (BalancesResponse r) => r.asOf == '2026-11-10'
          ? throw const FormatException('forma desconocida')
          : r.asOf,
    );

    expect(reading.value, '2026-11-09');
    expect(reading.staleBecause, const RequestFailure(FailureKind.unexpected));
    expect((await snapshots.read('balances'))!.readAt, nine);
  });

  test('si la copia no se pudo guardar, lo leído se muestra igual', () async {
    await db.customStatement('DROP TABLE read_snapshots');
    adapter.answer(200, balances('2026-11-09'));

    final Reading<String> reading = await readAsOf();

    expect(reading.value, '2026-11-09');
    expect(reading.isStale, isFalse);
  });

  test('una copia ilegible no se muestra: queda el fallo', () async {
    await snapshots.save('balances', '{"no":"es"}', nine);
    adapter.fail(const SocketException('sin red'));

    await expectLater(
      readAsOf(),
      throwsA(const RequestFailure(FailureKind.noAnswer)),
    );
  });

  test('la hora de la lectura es un instante en UTC', () async {
    now = DateTime(2026, 11, 9, 4); // hora local del teléfono
    adapter.answer(200, balances('2026-11-09'));

    final Reading<String> reading = await readAsOf();

    expect(reading.readAt.isUtc, isTrue);
    expect(reading.readAt.isAtSameMomentAs(now), isTrue);
  });
}

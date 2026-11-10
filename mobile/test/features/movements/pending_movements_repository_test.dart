import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/lock/app_lock.dart';
import 'package:mobile/core/lock/authenticator.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/storage/secure_store.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/core/storage/server_settings_store.dart';
import 'package:mobile/data/api/export.dart';
import 'package:mobile/data/local/local_database.dart';
import 'package:mobile/data/sync/movement_transport.dart';
import 'package:mobile/data/sync/pending_queue.dart';
import 'package:mobile/data/sync/pending_sender.dart';
import 'package:mobile/features/movements/data/pending_movements_repository.dart';

import '../../core/lock/app_lock_test.dart' show FakeAuthenticator;
import '../../data/sync/pending_sender_test.dart'
    show FakeSendTimer, untilSettled;
import '../../support/fake_secure_store.dart';
import '../../support/movement_samples.dart';
import '../../support/recording_adapter.dart';

/// Registrar un movimiento y lo que la interfaz ve de la cola, sin widgets.
void main() {
  const String first = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';

  late FakeAuthenticator authenticator;
  late RecordingAdapter adapter;
  late LocalDatabase db;
  late ProviderContainer container;

  MovementRequest expense(String id) => MovementRequestExpenseRequest(
    type: ExpenseRequestType.expense,
    id: id,
    date: '2026-11-02',
    description: 'Almuerzo',
    paidFrom: '0a3c0f5e-2b1d-4c8e-9f00-00000000000a',
    category: '0a3c0f5e-2b1d-4c8e-9f00-00000000000b',
    amountInCents: 1234,
  );

  ResponseBody reply(int status, Map<String, Object?> body) =>
      ResponseBody.fromString(
        jsonEncode(body),
        status,
        headers: <String, List<String>>{
          Headers.contentTypeHeader: <String>[Headers.jsonContentType],
        },
      );

  Future<PendingQueue> queue() => container.read(pendingQueueProvider.future);

  Future<PendingSummary> summary() =>
      container.read(pendingSummaryProvider.future);

  Future<void> unlock() async {
    authenticator.next = AuthOutcome.success;
    await container.read(appLockProvider.notifier).unlock();
  }

  setUp(() async {
    final FakeSecureStore secure = FakeSecureStore();
    authenticator = FakeAuthenticator();
    adapter = RecordingAdapter()
      ..handler = (RequestOptions o) async => reply(
        201,
        movementResponseBody((o.data as Map<String, Object?>)['id']! as String),
      );
    db = LocalDatabase(NativeDatabase.memory());
    container = ProviderContainer(
      overrides: [
        secureStoreProvider.overrideWithValue(secure),
        authenticatorProvider.overrideWithValue(authenticator),
        localDatabaseProvider.overrideWith((Ref ref) async => db),
        movementTransportProvider.overrideWithValue(
          MovementTransport(
            (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
          ),
        ),
        sendTimerProvider.overrideWithValue(FakeSendTimer()),
      ],
    )..listen(pendingSummaryProvider, (_, _) {});
    await ServerSettingsStore(secure).save(
      ServerSettings(
        address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
        token: ApiToken('0123456789abcdef' * 4),
      ),
    );
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  group('registrar', () {
    test('con la app abierta, se encola y sale de una vez', () async {
      await unlock();

      await (await queue()).record(expense(first));
      await untilSettled(() async => (await summary()).waiting == 0);

      expect(adapter.requests, hasLength(1));
      expect(adapter.last.uri.path, '/api/movements');
    });

    test('bloqueada, queda en la cola y no sale', () async {
      await (await queue()).record(expense(first));
      await untilSettled(() async => (await summary()).waiting == 1);

      expect(adapter.requests, isEmpty);
    });

    test('primero entra a la cola: sin red, no se pierde', () async {
      adapter.handler = (_) async => throw const SocketException('sin red');
      await unlock();

      await (await queue()).record(expense(first));
      await untilSettled(
        () async => (await summary()).problem == SendingProblem.noConnection,
      );

      expect((await summary()).waiting, 1);
    });

    test('dos veces el mismo es uno solo', () async {
      final PendingQueue movements = await queue();

      await movements.record(expense(first));
      await movements.record(expense(first));
      await untilSettled(() async => (await summary()).waiting == 1);

      expect((await summary()).waiting, 1);
    });
  });

  group('el resumen que se ve', () {
    test('sin nada, cero y sin problema', () async {
      final PendingSummary seen = await summary();

      expect(seen.waiting, 0);
      expect(seen.needingAttention, 0);
      expect(seen.problem, isNull);
    });

    test('dice cuántos necesitan atención', () async {
      adapter.handler = (_) async => reply(409, <String, Object?>{
        'type': 'urn:finance:error:duplicate-id',
      });
      await unlock();

      await (await queue()).record(expense(first));
      await untilSettled(() async => (await summary()).needingAttention == 1);

      expect((await summary()).waiting, 0);
    });

    for (final (int status, Map<String, List<String>> _, SendingProblem wanted)
        in <(int, Map<String, List<String>>, SendingProblem)>[
          (401, const <String, List<String>>{}, SendingProblem.token),
          (429, const <String, List<String>>{}, SendingProblem.tooManyAttempts),
          (503, const <String, List<String>>{}, SendingProblem.serverBusy),
          (500, const <String, List<String>>{}, SendingProblem.unexpected),
        ]) {
      test('un $status se ve como ${wanted.name}', () async {
        adapter.handler = (_) async => reply(status, <String, Object?>{});
        await unlock();

        await (await queue()).record(expense(first));
        await untilSettled(() async => (await summary()).problem == wanted);

        expect((await summary()).waiting, 1);
      });
    }

    test('la pausa se va cuando el envío vuelve a funcionar', () async {
      adapter.handler = (_) async => throw const SocketException('sin red');
      await unlock();
      await (await queue()).record(expense(first));
      await untilSettled(
        () async => (await summary()).problem == SendingProblem.noConnection,
      );

      adapter.handler = (RequestOptions o) async => reply(
        201,
        movementResponseBody((o.data as Map<String, Object?>)['id']! as String),
      );
      await container.read(pendingSenderProvider.notifier).wake();
      await untilSettled(() async => (await summary()).problem == null);

      expect((await summary()).waiting, 0);
    });
  });
}

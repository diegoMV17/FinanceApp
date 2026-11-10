import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:flutter/widgets.dart';
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
import 'package:mobile/data/local/pending_movements_store.dart';
import 'package:mobile/data/sync/movement_transport.dart';
import 'package:mobile/data/sync/pending_movement.dart';
import 'package:mobile/data/sync/pending_sender.dart';
import 'package:mobile/data/sync/send_verdict.dart';

import '../../core/lock/app_lock_test.dart' show FakeAuthenticator;
import '../../support/fake_secure_store.dart';
import '../../support/movement_samples.dart';
import '../../support/recording_adapter.dart';

/// El envío de la cola, sin widgets: el almacén de verdad sobre una base en
/// memoria, el `Dio` de verdad sobre un transporte falso, y un temporizador que
/// corre cuando el test lo dice.
void main() {
  const String first = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String second = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';
  const String third = '0a3c0f5e-2b1d-4c8e-9f00-000000000003';
  const String origin = 'https://finanzas.ejemplo.invalid';

  late FakeSecureStore secure;
  late FakeAuthenticator authenticator;
  late RecordingAdapter adapter;
  late FakeSendTimer timer;
  late LocalDatabase db;
  late PendingMovementsStore store;
  late ProviderContainer container;
  Object? databaseFailure;

  PendingMovement expense(String id) => PendingMovement.of(
    MovementRequestExpenseRequest(
      type: ExpenseRequestType.expense,
      id: id,
      date: '2026-11-02',
      description: 'Almuerzo',
      paidFrom: '0a3c0f5e-2b1d-4c8e-9f00-00000000000a',
      category: '0a3c0f5e-2b1d-4c8e-9f00-00000000000b',
      amountInCents: 1234,
    ),
  );

  ResponseBody reply(
    int status, [
    Object? body,
    Map<String, List<String>> headers = const <String, List<String>>{},
  ]) => ResponseBody.fromString(
    jsonEncode(body ?? <String, Object?>{}),
    status,
    headers: <String, List<String>>{
      Headers.contentTypeHeader: <String>['application/problem+json'],
      ...headers,
    },
  );

  String idOf(RequestOptions options) =>
      (options.data as Map<String, Object?>)['id']! as String;

  ResponseBody accept(RequestOptions options, [int status = 201]) =>
      reply(status, movementResponseBody(idOf(options)));

  Map<String, Object?> problem(String code) => <String, Object?>{
    'type': 'urn:finance:error:$code',
  };

  List<String> sent() => adapter.requests.map(idOf).toList();

  Future<List<String>> waiting() async => <String>[
    for (final PendingMovement pending in await store.waiting())
      pending.id.value,
  ];

  PendingSender sender() => container.read(pendingSenderProvider.notifier);
  PauseReason? pause() => container.read(pendingSenderProvider);

  Future<void> unlock() async {
    authenticator.next = AuthOutcome.success;
    await container.read(appLockProvider.notifier).unlock();
  }

  void lockIt() => container
      .read(appLockProvider.notifier)
      .onLifecycle(AppLifecycleState.paused);

  /// Desbloquea y espera a que termine la pasada que eso dispara.
  Future<void> unlockAndSend() async {
    await unlock();
    await sender().wake();
  }

  setUp(() async {
    secure = FakeSecureStore();
    authenticator = FakeAuthenticator();
    adapter = RecordingAdapter()..handler = (o) async => accept(o);
    timer = FakeSendTimer();
    db = LocalDatabase(NativeDatabase.memory());
    store = PendingMovementsStore(db);
    databaseFailure = null;
    container = ProviderContainer(
      overrides: [
        secureStoreProvider.overrideWithValue(secure),
        authenticatorProvider.overrideWithValue(authenticator),
        localDatabaseProvider.overrideWith((Ref ref) async {
          final Object? failure = databaseFailure;
          if (failure != null) {
            throw failure;
          }
          return db;
        }),
        movementTransportProvider.overrideWithValue(
          MovementTransport(
            (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
          ),
        ),
        sendTimerProvider.overrideWithValue(timer),
      ],
    )..listen(pendingSenderProvider, (_, _) {});
    await ServerSettingsStore(secure).save(
      ServerSettings(
        address: ServerAddress.parse(origin),
        token: ApiToken('0123456789abcdef' * 4),
      ),
    );
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  group('bloqueada, nada sale a la red', () {
    test('aunque se le pida enviar', () async {
      await store.enqueue(expense(first));

      await sender().wake();

      expect(adapter.requests, isEmpty);
      expect(await waiting(), <String>[first]);
    });

    test('al bloquearse a mitad de pasada, no sale el siguiente', () async {
      await store.enqueue(expense(first));
      await store.enqueue(expense(second));
      adapter.handler = (RequestOptions o) async {
        lockIt();
        return accept(o);
      };

      await unlockAndSend();

      expect(sent(), <String>[first]);
      // El que ya estaba en camino sí se atiende: el servidor ya lo registró.
      expect(await waiting(), <String>[second]);
    });

    test('un temporizador que vence bloqueada no envía', () async {
      await store.enqueue(expense(first));
      adapter.handler = (_) async => throw const SocketException('sin red');
      await unlockAndSend();
      adapter.requests.clear();

      lockIt();
      await timer.fireAnyway();

      expect(adapter.requests, isEmpty);
    });

    test('bloquearse cancela el reintento programado', () async {
      await store.enqueue(expense(first));
      adapter.handler = (_) async => throw const SocketException('sin red');
      await unlockAndSend();
      expect(timer.scheduled, isNotNull);

      lockIt();

      expect(timer.scheduled, isNull);
    });
  });

  test('al desbloquear envía sola lo que había', () async {
    await store.enqueue(expense(first));

    await unlock();
    await untilSettled(() async => (await waiting()).isEmpty);

    expect(sent(), <String>[first]);
  });

  group('lo que viaja', () {
    test('el cuerpo exacto que se encoló, a la ruta de movimientos', () async {
      final PendingMovement movement = expense(first);
      await store.enqueue(movement);

      await unlockAndSend();

      expect(adapter.last.method, 'POST');
      expect(adapter.last.uri.toString(), '$origin/api/movements');
      expect(adapter.last.data, jsonDecode(movement.body));
    });

    test('con el token, en su cabecera', () async {
      await store.enqueue(expense(first));

      await unlockAndSend();

      expect(adapter.last.headers['Authorization'], startsWith('Bearer '));
    });
  });

  group('aceptado', () {
    test('201: sale de la cola, y en orden', () async {
      await store.enqueue(expense(first));
      await store.enqueue(expense(second));
      await store.enqueue(expense(third));

      await unlockAndSend();

      expect(sent(), <String>[first, second, third]);
      expect(await waiting(), isEmpty);
      expect(timer.scheduled, isNull);
    });

    test('200 (ya estaba aplicado): también', () async {
      await store.enqueue(expense(first));
      adapter.handler = (RequestOptions o) async => accept(o, 200);

      await unlockAndSend();

      expect(await waiting(), isEmpty);
      expect(pause(), isNull);
    });
  });

  group('un 200 que no es de este movimiento no borra nada', () {
    for (final (String name, Object? body) in <(String, Object?)>[
      ('otro id', movementResponseBody(second)),
      ('sin id', <String, Object?>{}),
    ]) {
      test(name, () async {
        await store.enqueue(expense(first));
        adapter.handler = (_) async => reply(200, body);

        await unlockAndSend();

        expect(await waiting(), <String>[first]);
        expect(pause(), PauseReason.unexpected);
        expect(timer.scheduled, isNotNull);
      });
    }

    test('una página de un portal de Wi-Fi', () async {
      await store.enqueue(expense(first));
      adapter.handler = (_) async => ResponseBody.fromString(
        '<html>Inicia sesión</html>',
        200,
        headers: <String, List<String>>{
          Headers.contentTypeHeader: <String>['text/html'],
        },
      );

      await unlockAndSend();

      expect(await waiting(), <String>[first]);
      expect(pause(), PauseReason.unexpected);
    });
  });

  group(
    'lo que el servidor rechaza no se reintenta y no frena a los demás',
    () {
      test('un 409 se marca, y un 409 nunca es éxito', () async {
        await store.enqueue(expense(first));
        await store.enqueue(expense(second));
        adapter.handler = (RequestOptions o) async =>
            idOf(o) == first ? reply(409, problem('duplicate-id')) : accept(o);

        await unlockAndSend();

        expect(sent(), <String>[first, second]);
        expect(await waiting(), isEmpty);
        expect(await store.counts.first, const PendingCounts(0, 1));
        expect(pause(), isNull);

        adapter.requests.clear();
        await sender().wake();
        expect(adapter.requests, isEmpty);
      });

      test('un 422 igual', () async {
        await store.enqueue(expense(first));
        await store.enqueue(expense(second));
        adapter.handler = (RequestOptions o) async => idOf(o) == first
            ? reply(422, problem('archived-account'))
            : accept(o);

        await unlockAndSend();

        expect(await store.counts.first, const PendingCounts(0, 1));
      });

      test('un 404 que no es de la API sí se reintenta', () async {
        await store.enqueue(expense(first));
        adapter.handler = (_) async => reply(404);

        await unlockAndSend();

        expect(await waiting(), <String>[first]);
        expect(pause(), PauseReason.unexpected);
      });
    },
  );

  group('401: el token ya no sirve', () {
    setUp(() async {
      await store.enqueue(expense(first));
      await store.enqueue(expense(second));
      adapter.handler = (_) async => reply(401, problem('unauthorized'));
      await unlockAndSend();
    });

    test('no marca nada y detiene la pasada', () async {
      expect(sent(), <String>[first]);
      expect(await waiting(), <String>[first, second]);
      expect(await store.counts.first, const PendingCounts(2, 0));
      expect(pause(), PauseReason.unauthorized);
    });

    test('no programa reintentos: sumaría fallos en el servidor', () {
      expect(timer.scheduled, isNull);
    });

    test('al volver a pedirlo con el token bueno, sale todo', () async {
      adapter.handler = (RequestOptions o) async => accept(o);

      await sender().wake();

      expect(await waiting(), isEmpty);
      expect(pause(), isNull);
    });
  });

  group('429: demasiados intentos', () {
    test('no marca nada, y espera lo que dice el servidor', () async {
      await store.enqueue(expense(first));
      adapter.handler = (_) async =>
          reply(429, problem('too-many-attempts'), <String, List<String>>{
            'retry-after': <String>['60'],
          });

      await unlockAndSend();

      expect(await waiting(), <String>[first]);
      expect(pause(), PauseReason.tooManyAttempts);
      expect(timer.scheduled, const Duration(seconds: 60));
    });
  });

  group('503 con Retry-After', () {
    setUp(() async {
      await store.enqueue(expense(first));
      adapter.handler = (_) async =>
          reply(503, problem('book-busy'), <String, List<String>>{
            'retry-after': <String>['30'],
          });
      await unlockAndSend();
    });

    test('espera ese tiempo', () {
      expect(pause(), PauseReason.busy);
      expect(timer.scheduled, const Duration(seconds: 30));
    });

    test('antes de ese tiempo no se manda nada, ni aunque se pida', () async {
      adapter.requests.clear();
      timer.advance(const Duration(seconds: 10));

      await sender().wake();

      expect(adapter.requests, isEmpty);
      expect(timer.scheduled, const Duration(seconds: 20));
    });

    test('pasado ese tiempo se reintenta', () async {
      adapter
        ..requests.clear()
        ..handler = (RequestOptions o) async => accept(o);

      await timer.fire();

      expect(sent(), <String>[first]);
      expect(await waiting(), isEmpty);
      expect(pause(), isNull);
    });

    test('bloquearse y volver no se salta la espera', () async {
      adapter.requests.clear();
      timer.advance(const Duration(seconds: 10));

      lockIt();
      await unlockAndSend();

      expect(adapter.requests, isEmpty);
      expect(timer.scheduled, const Duration(seconds: 20));
    });
  });

  group('sin respuesta', () {
    setUp(() async {
      await store.enqueue(expense(first));
      await store.enqueue(expense(second));
      adapter.handler = (_) async => throw const SocketException('sin red');
      await unlockAndSend();
    });

    test('todo sigue pendiente y la pasada se detiene', () async {
      expect(sent(), <String>[first]);
      expect(await waiting(), <String>[first, second]);
      expect(pause(), PauseReason.noAnswer);
    });

    test('se reintenta con el mismo id y el mismo cuerpo', () async {
      final Object? before = adapter.last.data;

      await timer.fire();

      expect(sent(), <String>[first, first]);
      expect(adapter.last.data, before);
    });

    test('la espera crece, con su tope', () async {
      expect(timer.scheduled, const Duration(seconds: 5));
      await timer.fire();
      expect(timer.scheduled, const Duration(seconds: 10));
      await timer.fire();
      expect(timer.scheduled, const Duration(seconds: 20));
      for (int i = 0; i < 12; i++) {
        await timer.fire();
      }
      expect(timer.scheduled, const Duration(minutes: 15));
    });

    test('al volver la red, la espera se reinicia', () async {
      adapter.handler = (RequestOptions o) async => accept(o);
      await timer.fire();
      expect(await waiting(), isEmpty);
      expect(pause(), isNull);

      await store.enqueue(expense(third));
      adapter.handler = (_) async => throw const SocketException('sin red');
      await sender().wake();

      expect(timer.scheduled, const Duration(seconds: 5));
    });

    test('registrar otro movimiento prueba de una vez, sin esperar', () async {
      adapter.requests.clear();

      await sender().wake();

      expect(sent(), <String>[first]);
    });
  });

  test(
    'la respuesta se perdió y el servidor ya lo tenía: el 200 lo saca',
    () async {
      await store.enqueue(expense(first));
      int attempts = 0;
      adapter.handler = (RequestOptions o) async {
        attempts++;
        if (attempts == 1) {
          // El servidor lo registró y la respuesta nunca llegó.
          throw DioException.receiveTimeout(
            timeout: const Duration(seconds: 20),
            requestOptions: o,
          );
        }
        return accept(o, 200);
      };

      await unlockAndSend();
      expect(await waiting(), <String>[first]);
      await timer.fire();

      expect(sent(), <String>[first, first]);
      expect(await waiting(), isEmpty);
    },
  );

  group('un solo envío a la vez', () {
    test('pedirlo dos veces no manda el mismo movimiento dos veces', () async {
      await store.enqueue(expense(first));
      final Completer<void> answer = Completer<void>();
      adapter.handler = (RequestOptions o) async {
        await answer.future;
        return accept(o);
      };
      await unlock();
      final Future<void> one = sender().wake();
      final Future<void> other = sender().wake();
      await untilSettled(() async => adapter.requests.isNotEmpty);

      answer.complete();
      await Future.wait(<Future<void>>[one, other]);

      expect(sent(), <String>[first]);
    });

    test('lo que se encola durante una pasada también sale', () async {
      await store.enqueue(expense(first));
      final Completer<void> answer = Completer<void>();
      adapter.handler = (RequestOptions o) async {
        await answer.future;
        return accept(o);
      };
      await unlock();
      final Future<void> running = sender().wake();
      await untilSettled(() async => adapter.requests.isNotEmpty);

      await store.enqueue(expense(second));
      final Future<void> again = sender().wake();
      answer.complete();
      await Future.wait(<Future<void>>[running, again]);

      expect(sent(), <String>[first, second]);
      expect(await waiting(), isEmpty);
    });
  });

  group('lo que no deja enviar', () {
    test('sin dirección ni token no hay a quién', () async {
      await store.enqueue(expense(first));
      await ServerSettingsStore(secure).clear();
      container.invalidate(serverSettingsProvider);

      await unlockAndSend();

      expect(adapter.requests, isEmpty);
      expect(await waiting(), <String>[first]);
    });

    test('con la base rota no sale nada y no revienta', () async {
      databaseFailure = StateError('la base no abrió');
      container.invalidate(localDatabaseProvider);

      await unlockAndSend();

      expect(adapter.requests, isEmpty);
    });
  });

  test(
    'si la base falla a mitad, no revienta y lo vuelve a intentar',
    () async {
      await unlockAndSend();
      await db.close();

      await sender().wake();

      expect(adapter.requests, isEmpty);
      expect(pause(), PauseReason.unexpected);
      expect(timer.scheduled, isNotNull);
    },
  );

  test('al cerrarse, cancela el reintento', () async {
    await store.enqueue(expense(first));
    adapter.handler = (_) async => throw const SocketException('sin red');
    await unlockAndSend();
    expect(timer.scheduled, isNotNull);

    container.dispose();

    expect(timer.scheduled, isNull);
  });

  group('el temporizador de verdad', () {
    test('corre pasado el tiempo', () async {
      final SystemSendTimer real = SystemSendTimer();
      final Completer<void> ran = Completer<void>();

      real.schedule(
        const Duration(milliseconds: 1),
        () async => ran.complete(),
      );

      await ran.future.timeout(const Duration(seconds: 5));
    });

    test('cancelado, no corre', () async {
      final SystemSendTimer real = SystemSendTimer();
      bool ran = false;

      real
        ..schedule(const Duration(milliseconds: 1), () async => ran = true)
        ..cancel();
      await Future<void>.delayed(const Duration(milliseconds: 20));

      expect(ran, isFalse);
    });

    test('programar otra vez reemplaza lo anterior', () async {
      final SystemSendTimer real = SystemSendTimer();
      int runs = 0;

      real
        ..schedule(const Duration(milliseconds: 1), () async => runs += 10)
        ..schedule(const Duration(milliseconds: 1), () async => runs += 1);
      await Future<void>.delayed(const Duration(milliseconds: 20));

      expect(runs, 1);
    });
  });
}

/// Espera a que se cumpla una condición que depende de trabajo asíncrono.
Future<void> untilSettled(Future<bool> Function() done) async {
  for (int i = 0; i < 200; i++) {
    if (await done()) {
      return;
    }
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
  fail('no terminó a tiempo');
}

/// Un temporizador que corre cuando el test lo dice, con su propio reloj.
class FakeSendTimer implements SendTimer {
  DateTime _now = DateTime.utc(2026, 11, 4);
  Duration? scheduled;
  Future<void> Function()? _callback;

  void advance(Duration by) => _now = _now.add(by);

  /// Pasa el tiempo programado y corre lo que había, como al vencer.
  Future<void> fire() async {
    advance(scheduled!);
    await fireAnyway();
  }

  /// Corre lo último que se programó, aunque ya se haya cancelado: como un
  /// temporizador que venció justo cuando alguien lo cancelaba.
  Future<void> fireAnyway() async {
    final Future<void> Function()? callback = _callback;
    scheduled = null;
    await callback?.call();
  }

  @override
  DateTime get now => _now;

  @override
  void schedule(Duration after, Future<void> Function() callback) {
    scheduled = after;
    _callback = callback;
  }

  @override
  void cancel() => scheduled = null;
}

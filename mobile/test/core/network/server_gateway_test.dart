import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/errors/request_failure.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/server_gateway.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/data/api/export.dart';

import '../../support/recording_adapter.dart';

/// La puerta por la que las pantallas hablan con el servidor: bloqueada, nada
/// sale; y lo que vuelve mal llega clasificado, nunca como un error de Dio.
void main() {
  final ServerSettings settings = ServerSettings(
    address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
    token: ApiToken(
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    ),
  );

  late RecordingAdapter adapter;
  late bool open;
  late ServerGateway gateway;

  Map<String, Object?> problem(String code) => <String, Object?>{
    'type': 'urn:finance:error:$code',
    'title': 'x',
    'status': 409,
    'detail': 'Texto del servidor',
  };

  Future<Object?> failureOf(Future<Object?> Function() call) async {
    try {
      await call();
    } on Object catch (failure) {
      return failure;
    }
    fail('no falló');
  }

  setUp(() {
    adapter = RecordingAdapter();
    open = true;
    gateway = ServerGateway(
      settings,
      () => open,
      (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
    );
  });

  Future<BalancesResponse> balances() =>
      gateway.call((FinanceApi api) => api.book.bookBalances());

  test('contesta lo que dice la API', () async {
    adapter.answer(200, <String, Object?>{
      'asOf': '2026-11-02',
      'accounts': <Object?>[],
    });

    expect((await balances()).asOf, '2026-11-02');
    expect(adapter.last.headers['Authorization'], startsWith('Bearer '));
  });

  test('bloqueada, la petición ni sale', () async {
    open = false;

    expect(await failureOf(balances), const RequestFailure(FailureKind.locked));
    expect(adapter.requests, isEmpty);
  });

  group('lo que vuelve mal llega clasificado', () {
    for (final (String name, int status, Object? body, RequestFailure expected)
        in <(String, int, Object?, RequestFailure)>[
          (
            '401',
            401,
            problem('unauthorized'),
            const RequestFailure(FailureKind.unauthorized),
          ),
          (
            '429',
            429,
            problem('too-many-attempts'),
            const RequestFailure(FailureKind.tooManyAttempts),
          ),
          (
            '503',
            503,
            problem('book-busy'),
            const RequestFailure(FailureKind.busy),
          ),
          (
            '409 de la API',
            409,
            problem('duplicate-account-name'),
            const RequestFailure.refused(
              'urn:finance:error:duplicate-account-name',
            ),
          ),
          (
            '422 de la API',
            422,
            problem('same-account-transfer'),
            const RequestFailure.refused(
              'urn:finance:error:same-account-transfer',
            ),
          ),
          (
            'un 404 que no es de la API',
            404,
            <String, Object?>{'error': 'Not Found'},
            const RequestFailure(FailureKind.unexpected),
          ),
          (
            '500',
            500,
            problem('internal-error'),
            const RequestFailure(FailureKind.unexpected),
          ),
          (
            'un 200 que no es la API',
            200,
            <String, Object?>{'hola': 'portal'},
            const RequestFailure(FailureKind.unexpected),
          ),
        ]) {
      test(name, () async {
        adapter.answer(status, body);

        expect(await failureOf(balances), expected);
      });
    }

    test('sin respuesta', () async {
      adapter.fail(const SocketException('sin red'));

      expect(
        await failureOf(balances),
        const RequestFailure(FailureKind.noAnswer),
      );
    });
  });
}

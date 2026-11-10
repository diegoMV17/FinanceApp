import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/data/api/export.dart';
import 'package:mobile/data/sync/movement_id.dart';
import 'package:mobile/data/sync/send_verdict.dart';

import '../../support/movement_samples.dart';

/// Qué significa cada respuesta del servidor para un movimiento de la cola. Es
/// donde se decide si un gasto se da por registrado, así que cada caso va aquí,
/// sin red y sin base.
void main() {
  final MovementId id = MovementId.parse(
    '0a3c0f5e-2b1d-4c8e-9f00-000000000001',
  );
  const String conflictType = 'urn:finance:error:duplicate-id';
  const String rulesType = 'urn:finance:error:archived-account';

  DioException answer(
    int status, {
    Object? body,
    Map<String, List<String>> headers = const <String, List<String>>{},
  }) {
    final RequestOptions options = RequestOptions(path: '/api/movements');
    return DioException.badResponse(
      statusCode: status,
      requestOptions: options,
      response: Response<Object?>(
        requestOptions: options,
        statusCode: status,
        data: body,
        headers: Headers.fromMap(headers),
      ),
    );
  }

  Map<String, Object?> problem(String type) => <String, Object?>{
    'type': type,
    'title': 'x',
  };

  group('un 201 o un 200', () {
    test('con el id del movimiento: aceptado', () {
      expect(
        verdictForResponse(
          id,
          MovementResponse.fromJson(movementResponseBody(id.value)),
        ),
        isA<Accepted>(),
      );
    });

    test('con otro id no es la respuesta a esta petición', () {
      final SendVerdict verdict = verdictForResponse(
        id,
        MovementResponse.fromJson(
          movementResponseBody('0a3c0f5e-2b1d-4c8e-9f00-000000000002'),
        ),
      );

      expect(verdict, isA<Paused>());
      expect((verdict as Paused).reason, PauseReason.unexpected);
    });
  });

  group('un 409 de la API', () {
    test('es un conflicto y guarda su type', () {
      final SendVerdict verdict = verdictForFailure(
        answer(409, body: problem(conflictType)),
      );

      expect(verdict, isA<Conflict>());
      expect((verdict as Conflict).problemType, conflictType);
    });
  });

  group('otro 4xx de la API se rechaza y guarda su type', () {
    for (final int status in <int>[400, 404, 405, 413, 415, 422]) {
      test('$status', () {
        final SendVerdict verdict = verdictForFailure(
          answer(status, body: problem(rulesType)),
        );

        expect(verdict, isA<Rejected>());
        expect((verdict as Rejected).problemType, rulesType);
      });
    }
  });

  group('un 4xx que no es de la API no dice nada del movimiento', () {
    // Un 404 de Tailscale Serve mal configurado rechazaría la cola entera.
    for (final (String name, Object? body) in <(String, Object?)>[
      ('sin cuerpo', null),
      ('una página', '<html>404</html>'),
      ('otro JSON', <String, Object?>{'error': 'no'}),
      ('otro type', <String, Object?>{'type': 'about:blank'}),
      ('un type que no es texto', <String, Object?>{'type': 7}),
    ]) {
      test(name, () {
        for (final int status in <int>[404, 409, 422]) {
          final SendVerdict verdict = verdictForFailure(
            answer(status, body: body),
          );

          expect(verdict, isA<Paused>());
          expect((verdict as Paused).reason, PauseReason.unexpected);
        }
      });
    }
  });

  group('401 y 429 no son culpa del movimiento: se pausa', () {
    test('401, aunque traiga un Problem de la API', () {
      final SendVerdict verdict = verdictForFailure(
        answer(401, body: problem('urn:finance:error:unauthorized')),
      );

      expect(verdict, isA<Paused>());
      expect((verdict as Paused).reason, PauseReason.unauthorized);
    });

    test('429, y dice cuánto esperar', () {
      final SendVerdict verdict = verdictForFailure(
        answer(
          429,
          body: problem('urn:finance:error:too-many-attempts'),
          headers: <String, List<String>>{
            'retry-after': <String>['60'],
          },
        ),
      );

      expect(verdict, isA<Paused>());
      expect((verdict as Paused).reason, PauseReason.tooManyAttempts);
      expect(verdict.retryAfter, const Duration(seconds: 60));
    });
  });

  group('503: el libro está ocupado', () {
    Paused busy(List<String>? retryAfter) => verdictForFailure(
      answer(503, headers: <String, List<String>>{'retry-after': ?retryAfter}),
    ) as Paused;

    test('espera lo que dice Retry-After', () {
      expect(busy(<String>['7']).reason, PauseReason.busy);
      expect(busy(<String>['7']).retryAfter, const Duration(seconds: 7));
    });

    test('un Retry-After exagerado no congela la cola: tope de una hora', () {
      expect(busy(<String>['99999']).retryAfter, maxRetryAfter);
      expect(maxRetryAfter, const Duration(hours: 1));
    });

    test('un número que desborda el Duration también se queda en el tope', () {
      // Sin esto daría una espera negativa: reintentar de inmediato, sin parar.
      expect(busy(<String>['18446744073709']).retryAfter, maxRetryAfter);
    });

    for (final (String name, List<String>? value) in <(String, List<String>?)>[
      ('sin cabecera', null),
      ('una fecha', <String>['Wed, 21 Oct 2026 07:28:00 GMT']),
      ('negativo', <String>['-5']),
      ('cero', <String>['0']),
      ('basura', <String>['pronto']),
      ('un número sin fin', <String>['999999999999999999999999']),
      ('dos valores', <String>['1', '2']),
    ]) {
      test('$name: se ignora y queda la espera creciente', () {
        expect(busy(value).reason, PauseReason.busy);
        expect(busy(value).retryAfter, isNull);
      });
    }
  });

  group('otra cosa del servidor se reintenta: el mismo id es seguro', () {
    for (final int status in <int>[500, 502, 504, 301, 302]) {
      test('$status', () {
        final SendVerdict verdict = verdictForFailure(answer(status));

        expect(verdict, isA<Paused>());
        expect((verdict as Paused).reason, PauseReason.unexpected);
      });
    }
  });

  group('sin respuesta', () {
    final RequestOptions options = RequestOptions(path: '/api/movements');

    for (final DioExceptionType type in <DioExceptionType>[
      DioExceptionType.connectionTimeout,
      DioExceptionType.sendTimeout,
      DioExceptionType.receiveTimeout,
      DioExceptionType.connectionError,
      DioExceptionType.badCertificate,
    ]) {
      test(type.name, () {
        final SendVerdict verdict = verdictForFailure(
          DioException(requestOptions: options, type: type),
        );

        expect(verdict, isA<Paused>());
        expect((verdict as Paused).reason, PauseReason.noAnswer);
      });
    }

    test('la red se cae a mitad', () {
      final SendVerdict verdict = verdictForFailure(
        DioException(
          requestOptions: options,
          error: const SocketException('sin red'),
        ),
      );

      expect((verdict as Paused).reason, PauseReason.noAnswer);
    });
  });

  test('un cuerpo que no se pudo leer no da por registrado nada', () {
    final SendVerdict verdict = verdictForFailure(TypeError());

    expect(verdict, isA<Paused>());
    expect((verdict as Paused).reason, PauseReason.unexpected);
  });

  group('la espera creciente', () {
    test('empieza en 5 segundos y se duplica', () {
      expect(backoffAfter(1), const Duration(seconds: 5));
      expect(backoffAfter(2), const Duration(seconds: 10));
      expect(backoffAfter(3), const Duration(seconds: 20));
    });

    test('se queda en 15 minutos', () {
      expect(backoffAfter(9), const Duration(minutes: 15));
      expect(backoffAfter(100000), const Duration(minutes: 15));
    });
  });
}

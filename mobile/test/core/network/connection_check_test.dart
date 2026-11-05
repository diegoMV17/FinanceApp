import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/connection_check.dart';
import 'package:mobile/core/storage/server_settings.dart';

import '../../support/recording_adapter.dart';

/// La prueba que se hace antes de guardar la dirección y el token: una lectura
/// cualquiera que exige token, y la respuesta traducida a algo que se le pueda
/// decir al usuario.
void main() {
  final ServerSettings settings = ServerSettings(
    address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
    token: ApiToken(
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    ),
  );

  late RecordingAdapter adapter;
  late ConnectionCheck check;

  setUp(() {
    adapter = RecordingAdapter();
    check = ConnectionCheck(
      (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
    );
  });

  test('200: conecta, y lo comprobó una ruta que exige token', () async {
    adapter.answer(200, <String, Object?>{
      'asOf': '2026-11-02',
      'balances': <Object?>[],
    });

    expect(await check.run(settings), ConnectionResult.connected);
    expect(adapter.last.uri.path, '/api/balances');
    expect(adapter.last.headers['Authorization'], startsWith('Bearer '));
  });

  test('401: el token no es el del servidor', () async {
    adapter.answer(401, _problem('unauthorized', 401));

    expect(await check.run(settings), ConnectionResult.wrongToken);
  });

  test('429: demasiados intentos fallidos desde esta dirección', () async {
    adapter.answer(429, _problem('too-many-attempts', 429));

    expect(await check.run(settings), ConnectionResult.tooManyAttempts);
  });

  test('sin respuesta: no se alcanzó el servidor', () async {
    adapter.fail(const SocketException('sin red'));

    expect(await check.run(settings), ConnectionResult.unreachable);
  });

  test('cualquier otra respuesta: algo inesperado, sin adivinar', () async {
    adapter.answer(503, _problem('book-busy', 503));

    expect(await check.run(settings), ConnectionResult.unexpected);
  });

  test('un 200 que no es la API tampoco es conectar', () async {
    // Otra cosa escuchando en esa dirección (una página de inicio de sesión
    // de una red, un servidor distinto) puede responder 200 con cualquier
    // cuerpo. Sin la forma de la respuesta de la API, no se guarda.
    adapter.answer(200, '<html>hola</html>');

    expect(await check.run(settings), ConnectionResult.unexpected);
  });
}

Map<String, Object?> _problem(String slug, int status) => <String, Object?>{
  'type': 'urn:finance:error:$slug',
  'title': 'x',
  'status': status,
  'detail': 'x',
};

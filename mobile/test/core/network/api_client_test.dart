import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/auth_interceptor.dart';
import 'package:mobile/core/storage/server_settings.dart';

import '../../support/recording_adapter.dart';

void main() {
  const String token =
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
  final ServerSettings settings = ServerSettings(
    address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
    token: ApiToken(token),
  );

  late RecordingAdapter adapter;
  late Dio dio;

  setUp(() {
    adapter = RecordingAdapter();
    dio = dioFor(settings)..httpClientAdapter = adapter;
  });

  test('habla con la dirección configurada', () async {
    await dio.get<Object?>('/api/balances');

    expect(
      adapter.last.uri.toString(),
      'https://finanzas.ejemplo.invalid/api/balances',
    );
  });

  test('lleva el token en Authorization', () async {
    await dio.get<Object?>('/api/balances');

    expect(adapter.last.headers['Authorization'], 'Bearer $token');
  });

  test('el token no sale hacia otro origen', () async {
    // Una URL absoluta a otro host (o al mismo por otro puerto) no recibe el
    // token: es la puerta del libro, y solo la conoce su servidor.
    for (final String elsewhere in <String>[
      'https://otro.ejemplo.invalid/api/balances',
      'https://finanzas.ejemplo.invalid:8443/api/balances',
    ]) {
      await dio.get<Object?>(elsewhere);

      expect(
        adapter.last.headers.containsKey('Authorization'),
        isFalse,
        reason: elsewhere,
      );
    }
  });

  test('nunca por http en claro, ni siquiera a su propio host', () async {
    await expectLater(
      dio.get<Object?>('http://finanzas.ejemplo.invalid/api/balances'),
      throwsA(isA<DioException>()),
    );
    expect(adapter.requests, isEmpty);
  });

  test('no sigue redirecciones: se llevarían el token a donde apunten', () {
    expect(dio.options.followRedirects, isFalse);
    expect(dio.options.maxRedirects, 0);
  });

  test('con tiempos de espera, para que una red muerta no cuelgue la app', () {
    expect(dio.options.connectTimeout, isNotNull);
    expect(dio.options.receiveTimeout, isNotNull);
    expect(dio.options.sendTimeout, isNotNull);
  });

  test('ningún interceptor de logs: imprimiría la cabecera y los cuerpos', () {
    expect(dio.interceptors.whereType<LogInterceptor>(), isEmpty);
    expect(dio.interceptors.whereType<AuthInterceptor>(), hasLength(1));
  });

  test('el cliente generado usa ese mismo Dio', () async {
    adapter.answer(200, <String, Object?>{
      'asOf': '2026-11-02',
      'accounts': <Object?>[],
    });

    await financeApiFor(dio).book.bookBalances();

    expect(adapter.last.headers['Authorization'], 'Bearer $token');
    expect(adapter.last.uri.path, '/api/balances');
  });
}

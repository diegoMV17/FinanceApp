import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:drift/native.dart';
import 'package:mobile/core/network/api_client.dart';
import 'package:mobile/core/network/server_gateway.dart';
import 'package:mobile/core/storage/server_settings.dart';
import 'package:mobile/data/local/local_database.dart';
import 'package:mobile/data/local/read_snapshots_store.dart';
import 'package:mobile/data/reading/read_through.dart';

import 'recording_adapter.dart';

/// Lo que responde el servidor falso a una petición: estado y cuerpo.
typedef Answer = (int status, Object? body);

/// Un servidor falso por ruta, con la puerta, la base en memoria y el lector
/// de verdad: los repositorios se prueban con todo lo suyo, sin red.
class ServerFixture {
  ServerFixture() {
    adapter.handler = (RequestOptions options) async {
      final String route = '${options.method} ${options.uri.path}';
      final Answer Function(RequestOptions)? answer = routes[route];
      if (offline) {
        throw DioException.connectionError(
          requestOptions: options,
          reason: 'sin red',
        );
      }
      final (int status, Object? body) = answer == null
          ? (404, <String, Object?>{'error': 'Not Found'})
          : answer(options);
      return ResponseBody.fromString(
        jsonEncode(body),
        status,
        headers: <String, List<String>>{
          Headers.contentTypeHeader: <String>[Headers.jsonContentType],
        },
      );
    };
  }

  static final ServerSettings settings = ServerSettings(
    address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
    token: ApiToken(
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    ),
  );

  final RecordingAdapter adapter = RecordingAdapter();
  final LocalDatabase db = LocalDatabase(NativeDatabase.memory());

  /// `'GET /api/summary'` → lo que contesta.
  final Map<String, Answer Function(RequestOptions)> routes =
      <String, Answer Function(RequestOptions)>{};

  bool open = true;
  bool offline = false;
  DateTime now = DateTime.utc(2026, 11, 9, 9);

  late final ServerGateway gateway = ServerGateway(
    settings,
    () => open,
    (ServerSettings s) => dioFor(s)..httpClientAdapter = adapter,
  );

  late final ReadThrough reader = ReadThrough(
    gateway,
    ReadSnapshotsStore(db),
    () => now,
  );

  void answer(String route, int status, Object? body) =>
      routes[route] = (_) => (status, body);

  /// Lo que se envió en las peticiones a esa ruta, en orden.
  List<Object?> sent(String route) => <Object?>[
    for (final RequestOptions r in adapter.requests)
      if ('${r.method} ${r.uri.path}' == route) r.data,
  ];

  Future<void> close() => db.close();
}

/// Un `Problem` de la API.
Map<String, Object?> apiProblem(String code, int status) => <String, Object?>{
  'type': 'urn:finance:error:$code',
  'title': 'x',
  'status': status,
  'detail': 'Texto fijo del servidor',
};

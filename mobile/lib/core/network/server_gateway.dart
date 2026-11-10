import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/api/finance_api.dart';
import '../errors/request_failure.dart';
import '../lock/app_lock.dart';
import '../storage/server_settings.dart';
import '../storage/server_settings_store.dart';
import 'api_client.dart';
import 'connection_check.dart';

/// La puerta por la que los repositorios hablan con el servidor configurado.
///
/// - Bloqueada, la petición ni sale: el token no viaja mientras nadie
///   desbloqueó. Se pregunta en cada llamada, no al construirla.
/// - Cada llamada abre su `Dio` ([dioFor]: el token solo hacia el origen
///   configurado, sin redirecciones ni logs) y lo cierra al terminar.
/// - Lo que vuelve mal llega como [RequestFailure], nunca como un error de
///   Dio: ese trae la petición y la respuesta enteras, y quien lo imprima
///   imprimiría el token y los montos.
class ServerGateway {
  ServerGateway(this._settings, this._isOpen, [this._dioFor = dioFor]);

  final ServerSettings _settings;
  final bool Function() _isOpen;
  final Dio Function(ServerSettings) _dioFor;

  Future<T> call<T>(Future<T> Function(FinanceApi api) request) async {
    if (!_isOpen()) {
      throw const RequestFailure(FailureKind.locked);
    }
    final Dio dio = _dioFor(_settings);
    try {
      return await request(financeApiFor(dio));
    } on Object catch (failure) {
      throw requestFailureOf(failure);
    } finally {
      dio.close();
    }
  }
}

/// Qué clase de fallo es. Es la misma lectura que hace la cola en
/// `send_verdict.dart`, para lo que no es un movimiento encolado: un 4xx solo
/// es un rechazo si trae un `Problem` de la API, porque un proxy no puede
/// rechazar nada en nombre del servidor.
RequestFailure requestFailureOf(Object failure) {
  if (failure is RequestFailure) {
    return failure;
  }
  if (failure is! DioException) {
    // El cuerpo no tenía la forma de la API: un 200 de otra cosa.
    return const RequestFailure(FailureKind.unexpected);
  }
  if (failure.type != DioExceptionType.badResponse) {
    return RequestFailure(
      ConnectionCheck.noAnswer(failure)
          ? FailureKind.noAnswer
          : FailureKind.unexpected,
    );
  }
  final int? status = failure.response?.statusCode;
  final String? problemType = apiProblemTypeOf(failure.response?.data);
  return switch (status) {
    401 => const RequestFailure(FailureKind.unauthorized),
    429 => const RequestFailure(FailureKind.tooManyAttempts),
    503 => const RequestFailure(FailureKind.busy),
    final int code when code >= 400 && code < 500 && problemType != null =>
      RequestFailure.refused(problemType),
    _ => const RequestFailure(FailureKind.unexpected),
  };
}

/// El `type` de un `Problem` de la API, o `null` si el cuerpo no es uno.
String? apiProblemTypeOf(Object? body) {
  if (body is! Map<String, Object?>) {
    return null;
  }
  final Object? type = body['type'];
  return type is String && type.startsWith(apiProblemPrefix) ? type : null;
}

/// La puerta del servidor guardado. Sin configuración no hay puerta: la
/// compuerta de la app ya muestra la pantalla de conexión.
final FutureProvider<ServerGateway> serverGatewayProvider =
    FutureProvider<ServerGateway>((Ref ref) async {
      final ServerSettings? settings = await ref.watch(
        serverSettingsProvider.future,
      );
      if (settings == null) {
        throw const RequestFailure(FailureKind.unauthorized);
      }
      final ProviderContainer container = ref.container;
      return ServerGateway(
        settings,
        () => container.read(appLockProvider).isOpen,
      );
    });

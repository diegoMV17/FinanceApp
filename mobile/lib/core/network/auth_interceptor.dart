import 'package:dio/dio.dart';

import '../storage/server_settings.dart';

/// Pone el token en cada petición a la API, y en ninguna otra.
///
/// El origen se compara entero (esquema, host y puerto): una URL absoluta a
/// otro host, o al mismo por otro puerto, sale sin `Authorization`. Y nada sale
/// por http, ni siquiera hacia el host configurado: la dirección guardada ya es
/// https, esto es la red de atrás para una URL absoluta escrita a mano algún
/// día.
///
/// No imprime nada. Ni el token, ni la petición, ni la respuesta.
class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._settings);

  final ServerSettings _settings;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final Uri uri = options.uri;
    if (uri.scheme != 'https') {
      handler.reject(
        DioException(
          requestOptions: options,
          type: DioExceptionType.cancel,
          error: const InsecureRequestRefused(),
        ),
      );
      return;
    }
    if (uri.origin == _settings.address.origin) {
      options.headers['Authorization'] = 'Bearer ${_settings.token.value}';
    } else {
      options.headers.remove('Authorization');
    }
    handler.next(options);
  }
}

/// Una petición por http en claro, cortada antes de salir.
class InsecureRequestRefused implements Exception {
  const InsecureRequestRefused();

  @override
  String toString() => 'InsecureRequestRefused';
}

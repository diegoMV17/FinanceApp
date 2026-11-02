import 'package:dio/dio.dart';

import '../../data/api/finance_api.dart';
import '../storage/server_settings.dart';
import 'auth_interceptor.dart';

/// El `Dio` con el que la app habla con su servidor.
///
/// - La dirección es la configurada; nada aquí trae una por omisión.
/// - `followRedirects` apagado: la API nunca redirige, y una redirección
///   seguida en silencio se llevaría el token a donde apunte.
/// - Tiempos de espera fijos: una red muerta no cuelga la app.
/// - El token lo pone [AuthInterceptor], solo hacia ese origen.
/// - **Ningún interceptor de logs**, ni en depuración: imprimiría la cabecera
///   con el token y los cuerpos con montos y nombres. Un test barre `lib/` y lo
///   impide.
/// - La verificación de certificados es la del sistema y no se toca.
Dio dioFor(ServerSettings settings) {
  return Dio(
    BaseOptions(
      baseUrl: settings.address.origin,
      connectTimeout: const Duration(seconds: 10),
      sendTimeout: const Duration(seconds: 20),
      receiveTimeout: const Duration(seconds: 20),
      followRedirects: false,
      maxRedirects: 0,
    ),
  )..interceptors.add(AuthInterceptor(settings));
}

/// El cliente generado desde el contrato, sobre ese `Dio`.
FinanceApi financeApiFor(Dio dio) => FinanceApi(dio);

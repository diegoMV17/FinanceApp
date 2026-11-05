import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../storage/server_settings.dart';
import 'api_client.dart';

/// Lo que se le puede decir al usuario después de probar la conexión.
enum ConnectionResult {
  /// La API respondió con el token: se puede guardar.
  connected,

  /// 401: el servidor está, el token no es el suyo.
  wrongToken,

  /// 429: demasiados tokens malos desde esta dirección; hay que esperar.
  tooManyAttempts,

  /// Sin respuesta: red caída, dirección equivocada, certificado rechazado.
  unreachable,

  /// Respondió algo que no es lo esperado. No se adivina qué.
  unexpected,
}

/// La prueba antes de guardar la dirección y el token.
///
/// `GET /api/balances` porque es una lectura que exige token y no escribe
/// nada. Solo [ConnectionResult.connected] deja guardar: así nunca queda
/// guardado un par que no funciona. Un 200 cuenta solo si el cuerpo tiene la
/// forma de la respuesta de la API; otra cosa contestando en esa dirección no
/// es el servidor.
class ConnectionCheck {
  const ConnectionCheck([this._dioFor = dioFor]);

  final Dio Function(ServerSettings) _dioFor;

  Future<ConnectionResult> run(ServerSettings settings) async {
    final Dio dio = _dioFor(settings);
    try {
      await financeApiFor(dio).book.bookBalances();
      return ConnectionResult.connected;
    } on DioException catch (failure) {
      if (failure.type == DioExceptionType.badResponse) {
        return switch (failure.response?.statusCode) {
          401 => ConnectionResult.wrongToken,
          429 => ConnectionResult.tooManyAttempts,
          _ => ConnectionResult.unexpected,
        };
      }
      return noAnswer(failure)
          ? ConnectionResult.unreachable
          : ConnectionResult.unexpected;
    } on Object {
      // El cuerpo no tenía la forma de la API (un 200 de otra cosa).
      return ConnectionResult.unexpected;
    } finally {
      dio.close();
    }
  }

  /// Lo que es «no se llegó»: los tiempos de espera, la conexión rechazada o
  /// caída, y el certificado que no pasó la verificación. Todo lo demás —un
  /// cuerpo que no es de la API, por ejemplo— llegó, y es otra cosa.
  static bool noAnswer(DioException failure) => switch (failure.type) {
    DioExceptionType.connectionTimeout ||
    DioExceptionType.sendTimeout ||
    DioExceptionType.receiveTimeout ||
    DioExceptionType.connectionError ||
    DioExceptionType.badCertificate => true,
    _ =>
      failure.error is SocketException ||
          failure.error is TlsException ||
          failure.error is HttpException,
  };
}

final Provider<ConnectionCheck> connectionCheckProvider =
    Provider<ConnectionCheck>((Ref ref) => const ConnectionCheck());

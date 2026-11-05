import 'dart:math';

import 'package:dio/dio.dart';

import '../../core/network/connection_check.dart';
import '../api/export.dart';
import 'movement_id.dart';

/// Lo que significa la respuesta del servidor para un movimiento de la cola.
sealed class SendVerdict {
  const SendVerdict();
}

/// 201 o 200 con este mismo id: registrado. Sale de la cola.
class Accepted extends SendVerdict {
  const Accepted();
}

/// 409 de la API: no se reintenta, lo resuelve el usuario.
class Conflict extends SendVerdict {
  const Conflict(this.problemType);

  final String problemType;
}

/// Otro 4xx de la API: no se reintenta y se muestra.
class Rejected extends SendVerdict {
  const Rejected(this.problemType);

  final String problemType;
}

/// Nada de esto dice algo del movimiento: sigue pendiente, con su mismo id, y
/// la pasada se detiene.
class Paused extends SendVerdict {
  const Paused(this.reason, [this.retryAfter]);

  final PauseReason reason;

  /// Lo que pidió el servidor; `null` deja la espera creciente.
  final Duration? retryAfter;
}

enum PauseReason {
  /// 401: el token ya no es el del servidor.
  unauthorized,

  /// 429: demasiados intentos fallidos desde esta red.
  tooManyAttempts,

  /// 503: el libro está ocupado.
  busy,

  /// No llegó: red caída, Tailscale apagado, tiempo de espera, certificado.
  noAnswer,

  /// Respondió algo que no es la API, o un error suyo. Reintentar es seguro.
  unexpected,
}

/// Lo más que se espera por un `Retry-After`: uno exagerado o malicioso no
/// puede congelar la cola.
const Duration maxRetryAfter = Duration(hours: 1);

const Duration _firstBackoff = Duration(seconds: 5);
const Duration _maxBackoff = Duration(minutes: 15);

/// El prefijo del `type` de todo error de la API. Un 4xx sin esto no lo escribió
/// el servidor: un proxy no puede rechazar un movimiento.
const String _apiProblemPrefix = 'urn:finance:error:';

/// El servidor respondió con éxito. Cuenta solo si la respuesta es de este
/// movimiento: sin eso, una página cualquiera con un 200 borraría un gasto sin
/// que estuviera registrado.
SendVerdict verdictForResponse(MovementId id, MovementResponse response) =>
    response.id == id.value
    ? const Accepted()
    : const Paused(PauseReason.unexpected);

/// El envío terminó en cualquier otra cosa.
SendVerdict verdictForFailure(Object failure) {
  if (failure is! DioException) {
    // El cuerpo de un 200 que no tenía la forma de la API.
    return const Paused(PauseReason.unexpected);
  }
  if (failure.type != DioExceptionType.badResponse) {
    return Paused(
      ConnectionCheck.noAnswer(failure)
          ? PauseReason.noAnswer
          : PauseReason.unexpected,
    );
  }
  final Response<Object?>? response = failure.response;
  final int? status = response?.statusCode;
  final String? problemType = _apiProblemType(response?.data);
  return switch (status) {
    401 => const Paused(PauseReason.unauthorized),
    429 => Paused(PauseReason.tooManyAttempts, _retryAfter(response)),
    503 => Paused(PauseReason.busy, _retryAfter(response)),
    409 when problemType != null => Conflict(problemType),
    final int code when code >= 400 && code < 500 && problemType != null =>
      Rejected(problemType),
    _ => const Paused(PauseReason.unexpected),
  };
}

/// La espera antes del reintento número [failures], contando desde 1.
Duration backoffAfter(int failures) {
  // El exponente con tope: duplicar sin fin daría la vuelta en un entero.
  final int doublings = min(failures - 1, 10);
  final Duration wait = _firstBackoff * (1 << doublings);
  return wait > _maxBackoff ? _maxBackoff : wait;
}

String? _apiProblemType(Object? body) {
  if (body is! Map<String, Object?>) {
    return null;
  }
  final Object? type = body['type'];
  return type is String && type.startsWith(_apiProblemPrefix) ? type : null;
}

/// El servidor manda segundos. Lo que no sea un solo número positivo se ignora,
/// y queda la espera creciente.
Duration? _retryAfter(Response<Object?>? response) {
  final List<String>? values = response?.headers['retry-after'];
  if (values == null || values.length != 1) {
    return null;
  }
  final int? seconds = int.tryParse(values.single);
  if (seconds == null || seconds <= 0) {
    return null;
  }
  // Se compara antes de armar el Duration: uno enorme desborda y queda negativo.
  return seconds > maxRetryAfter.inSeconds
      ? maxRetryAfter
      : Duration(seconds: seconds);
}

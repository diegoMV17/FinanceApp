import '../../core/errors/request_failure.dart';

/// Un 200 o 201 de una escritura cuenta solo si es la respuesta a esto mismo:
/// otra cosa contestando en esa dirección (un portal de Wi-Fi, un proxy) no
/// registró nada. Es la misma regla que la cola aplica a los movimientos.
void requireAnswerTo(String answered, String sent) {
  if (answered != sent) {
    throw const RequestFailure(FailureKind.unexpected);
  }
}

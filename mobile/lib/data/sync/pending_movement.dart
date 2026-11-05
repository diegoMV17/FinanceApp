import 'dart:convert';

import '../api/export.dart';
import 'movement_id.dart';

/// Un movimiento esperando su envío: el cuerpo exacto que viajará y su id.
///
/// Imposible de construir si el id no es el del cuerpo o el cuerpo no se puede
/// leer como un movimiento. Se guarda el texto y no se vuelve a armar: reenviar
/// otro contenido con el mismo id sería un 409.
class PendingMovement {
  factory PendingMovement.of(MovementRequest request) {
    final String body = jsonEncode(request.toJson());
    final Object? id = request.toJson()['id'];
    if (id is! String) {
      throw const FormatException('el movimiento no tiene id');
    }
    return PendingMovement.restored(id, body);
  }

  /// Lo que estaba guardado. Lanza [FormatException] si no cuadra.
  factory PendingMovement.restored(String id, String body) {
    final MovementId parsed = MovementId.parse(id);
    final MovementRequest request = _read(body);
    if (request.toJson()['id'] != id) {
      throw const FormatException('el id no es el del cuerpo');
    }
    return PendingMovement._(parsed, body, request);
  }

  const PendingMovement._(this.id, this.body, this.request);

  final MovementId id;
  final String body;
  final MovementRequest request;

  static MovementRequest _read(String body) {
    final Object? json = jsonDecode(body);
    if (json is! Map<String, Object?>) {
      throw const FormatException('un movimiento es un objeto');
    }
    try {
      return MovementRequest.fromJson(json);
    } on TypeError {
      // Lo que lanza el código generado cuando falta un campo. Hacia afuera,
      // una sola forma de decir que esto no es un movimiento.
      throw const FormatException('al movimiento le falta un campo');
    }
  }

  /// Solo el id: el cuerpo trae montos y descripciones, y un `toString` termina
  /// tarde o temprano en algún mensaje.
  @override
  String toString() => 'PendingMovement($id)';
}

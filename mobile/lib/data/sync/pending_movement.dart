import 'dart:convert';

import '../api/export.dart';
import 'movement_id.dart';

/// Un movimiento esperando su envío: el cuerpo exacto que viajará y su id.
///
/// Imposible de construir si el id no es el del cuerpo, si el cuerpo no se puede
/// leer como un movimiento o si lo que viajaría no es lo que se guardó: reenviar
/// otro contenido con el mismo id sería un 409. El cliente generado vuelve a
/// armar el JSON desde [request], y por eso se comprueba aquí que sale igual.
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
    final Map<String, Object?> stored = _decode(body);
    final MovementRequest request = _read(stored);
    if (stored['id'] != id) {
      throw const FormatException('el id no es el del cuerpo');
    }
    if (!_sameFields(stored, request.toJson())) {
      throw const FormatException('lo que viajaría no es lo que se guardó');
    }
    return PendingMovement._(parsed, body, request);
  }

  const PendingMovement._(this.id, this.body, this.request);

  final MovementId id;
  final String body;
  final MovementRequest request;

  static Map<String, Object?> _decode(String body) {
    final Object? json = jsonDecode(body);
    if (json is! Map<String, Object?>) {
      throw const FormatException('un movimiento es un objeto');
    }
    return json;
  }

  /// Los once cuerpos son planos: comparar campo por campo basta. Si un día uno
  /// anida algo, esto lo rechaza en vez de enviarlo, y un test lo dice.
  static bool _sameFields(Map<String, Object?> a, Map<String, Object?> b) =>
      a.length == b.length &&
      a.keys.every((String key) => b.containsKey(key) && a[key] == b[key]);

  static MovementRequest _read(Map<String, Object?> json) {
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

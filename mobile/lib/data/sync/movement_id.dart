import 'dart:math';

import '../../core/ids/uuid.dart';

/// El id de un movimiento, generado en el teléfono antes de encolarlo.
///
/// Es lo que hace seguro reintentar: el servidor reconoce el mismo id con el
/// mismo contenido como la misma operación, y responde 200 sin escribir. Un
/// UUID v4, siempre en minúsculas: aquí los ids se comparan como texto.
class MovementId {
  /// [random] solo para los tests; por omisión, el generador seguro del sistema.
  factory MovementId.generate([Random? random]) =>
      MovementId._(generateUuidV4(random));

  /// Lanza [FormatException] si no es un UUID v4 en su forma canónica.
  factory MovementId.parse(String text) {
    if (!isUuidV4(text)) {
      // Sin repetir el texto: viene de afuera de este objeto.
      throw const FormatException('un id de movimiento es un UUID v4');
    }
    return MovementId._(text);
  }

  const MovementId._(this.value);

  final String value;

  @override
  bool operator ==(Object other) => other is MovementId && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => value;
}

import 'dart:math';

/// El id de un movimiento, generado en el teléfono antes de encolarlo.
///
/// Es lo que hace seguro reintentar: el servidor reconoce el mismo id con el
/// mismo contenido como la misma operación, y responde 200 sin escribir. Un
/// UUID v4, siempre en minúsculas: aquí los ids se comparan como texto.
class MovementId {
  /// [random] solo para los tests; por omisión, el generador seguro del sistema.
  factory MovementId.generate([Random? random]) {
    final Random source = random ?? Random.secure();
    final List<int> bytes = List<int>.generate(16, (_) => source.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40; // La versión: 4.
    bytes[8] = (bytes[8] & 0x3f) | 0x80; // La variante: RFC 4122.
    final String hex = bytes
        .map((int byte) => byte.toRadixString(16).padLeft(2, '0'))
        .join();
    return MovementId._(
      '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
      '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}',
    );
  }

  /// Lanza [FormatException] si no es un UUID v4 en su forma canónica.
  factory MovementId.parse(String text) {
    if (!_v4.hasMatch(text)) {
      // Sin repetir el texto: viene de afuera de este objeto.
      throw const FormatException('un id de movimiento es un UUID v4');
    }
    return MovementId._(text);
  }

  const MovementId._(this.value);

  static final RegExp _v4 = RegExp(
    r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
  );

  final String value;

  @override
  bool operator ==(Object other) => other is MovementId && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => value;
}

import 'dart:math';

/// Los ids que genera el teléfono: UUID v4, siempre en minúsculas, porque
/// aquí los ids se comparan como texto.
///
/// Todo lo que se escribe lleva uno antes de salir (un movimiento, una
/// persona, una cuenta, un presupuesto). Es lo que hace seguro reintentar: el
/// servidor reconoce el mismo id con el mismo contenido como la misma
/// operación y responde 200 sin escribir.
///
/// [random] solo para los tests; por omisión, el generador seguro del sistema.
String generateUuidV4([Random? random]) {
  final Random source = random ?? Random.secure();
  final List<int> bytes = List<int>.generate(16, (_) => source.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // La versión: 4.
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // La variante: RFC 4122.
  final String hex = bytes
      .map((int byte) => byte.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
      '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

/// Un UUID v4 en su forma canónica, como los que genera [generateUuidV4].
bool isUuidV4(String text) => _v4.hasMatch(text);

/// Un UUID de cualquier versión: lo que llega del servidor no lo generó
/// necesariamente este teléfono.
bool isUuid(String text) => _any.hasMatch(text);

final RegExp _v4 = RegExp(
  r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
);

final RegExp _any = RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
);

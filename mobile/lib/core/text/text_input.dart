/// El formato del texto que escribe el usuario, antes de enviarlo.
///
/// Solo el formato: sin espacios de los lados y dentro del tope. Los topes son
/// los del contrato (un test los compara con `contract/openapi.json`) y se
/// cuentan en unidades UTF-16, que es como cuenta `@Size` en el borde del
/// servidor: un emoji cuenta dos. Lo demás —caracteres de control o
/// invisibles— lo rechaza el servidor, y la app muestra esa respuesta.
library;

/// El largo de una descripción de movimiento (`Transaction.MAX_DESCRIPTION_LENGTH`).
const int maxDescriptionLength = 200;

/// El largo del nombre de una cuenta o de una persona (`MAX_NAME_LENGTH`).
const int maxNameLength = 60;

enum TextProblem { blank, tooLong }

class TextInputException implements Exception {
  const TextInputException(this.problem);

  final TextProblem problem;

  // Sin repetir el texto: lo escribió el usuario.
  @override
  String toString() => 'TextInputException: ${problem.name}';
}

/// Un nombre: obligatorio y hasta [maxNameLength].
String checkedName(String text) {
  final String trimmed = text.trim();
  if (trimmed.isEmpty) {
    throw const TextInputException(TextProblem.blank);
  }
  return _within(trimmed, maxNameLength);
}

/// Una descripción: puede ir vacía, y hasta [maxDescriptionLength].
String checkedDescription(String text) =>
    _within(text.trim(), maxDescriptionLength);

String _within(String trimmed, int max) {
  if (trimmed.length > max) {
    throw const TextInputException(TextProblem.tooLong);
  }
  return trimmed;
}

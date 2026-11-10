import 'money.dart';

/// El monto más grande que se puede escribir, en centavos: diez mil millones
/// de pesos. Es el `maximum` del contrato para cada monto (el
/// `Entry.MAX_ABSOLUTE_CENTS` del servidor); un test lo compara con
/// `contract/openapi.json` para que no sea una segunda verdad.
const int maxAmountCents = 1000000000000;

/// Por qué lo escrito no es un monto.
enum AmountProblem { empty, malformed, notPositive, tooLarge }

class AmountInputException implements Exception {
  const AmountInputException(this.problem);

  final AmountProblem problem;

  // Sin repetir lo escrito: es un monto del usuario.
  @override
  String toString() => 'AmountInputException: ${problem.name}';
}

/// Lo que el usuario escribe como monto, en pesos colombianos: punto de
/// miles y coma decimal (`12.345,67`), con o sin el signo de pesos.
///
/// Pasa a centavos con enteros, sin tocar un `double`. Es estricto a
/// propósito: `12.5` no se adivina (¿doce mil quinientos o doce y medio?) y
/// un signo menos no se acepta, porque de qué lado va el monto lo decide el
/// servidor según el tipo de movimiento, no quien escribe.
Money parseAmount(String text) {
  String body = text.trim();
  if (body.startsWith(r'$')) {
    body = body.substring(1).trim();
  }
  if (body.isEmpty) {
    throw const AmountInputException(AmountProblem.empty);
  }
  final RegExpMatch? match = _amount.firstMatch(body);
  if (match == null) {
    throw const AmountInputException(AmountProblem.malformed);
  }
  final String units = match[1]!.replaceAll('.', '');
  final String decimals = (match[2] ?? '').padRight(2, '0');
  // Antes de convertir: un texto de treinta dígitos no cabe en el entero.
  if (units.length > _maxUnitDigits) {
    throw const AmountInputException(AmountProblem.tooLarge);
  }
  final int cents = int.parse(units) * 100 + int.parse(decimals);
  if (cents == 0) {
    throw const AmountInputException(AmountProblem.notPositive);
  }
  if (cents > maxAmountCents) {
    throw const AmountInputException(AmountProblem.tooLarge);
  }
  return Money.ofCents(cents);
}

/// El texto con el que se edita un monto ya escrito: el mismo formato que
/// [parseAmount] lee, sin el signo de pesos y sin centavos si son cero.
String amountText(Money amount) {
  final int absolute = amount.cents.abs();
  final String units = _withThousands(absolute ~/ 100);
  final int cents = absolute % 100;
  return cents == 0 ? units : '$units,${cents.toString().padLeft(2, '0')}';
}

/// Sin puntos o con grupos de tres; coma y uno o dos decimales. Solo los
/// dígitos 0-9: `\d` en Dart también es solo ASCII, pero escribirlo así no
/// deja dudas.
final RegExp _amount = RegExp(
  r'^([0-9]{1,3}(?:\.[0-9]{3})+|[0-9]+)(?:,([0-9]{1,2}))?$',
);

/// Diez mil millones de pesos tienen once dígitos; doce dejan margen para
/// rechazar con [AmountProblem.tooLarge] en vez de desbordar.
const int _maxUnitDigits = 12;

String _withThousands(int units) {
  final String digits = units.toString();
  final StringBuffer out = StringBuffer();
  for (int i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) {
      out.write('.');
    }
    out.write(digits[i]);
  }
  return out.toString();
}

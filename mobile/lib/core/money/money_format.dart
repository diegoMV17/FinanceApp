import 'money.dart';

/// El formato en pesos, armado desde los centavos.
///
/// Nunca pasando por `double`: convertir a coma flotante para mostrar es la
/// misma pérdida de precisión, solo que escondida en la última cifra que el
/// dueño sí lee.
extension MoneyFormat on Money {
  /// `$ 1.234.567,89`, y el signo delante de todo: `-$ 1.234,00`.
  String get inPesos {
    final int absolute = isNegative ? -cents : cents;
    final String units = _withThousands(absolute ~/ _centsPerUnit);
    final String decimals = (absolute % _centsPerUnit).toString().padLeft(
      2,
      '0',
    );
    final String sign = isNegative ? '-' : '';
    return '$sign\$ $units,$decimals';
  }
}

const int _centsPerUnit = 100;

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

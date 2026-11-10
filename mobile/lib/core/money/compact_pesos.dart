import 'money.dart';

/// El formato corto de los ejes y las etiquetas de los gráficos: `$ 850 mil`,
/// `$ 1,5 M`, `$ 2,3 mil M`. Armado con enteros desde los centavos, igual que
/// `inPesos`, que sigue siendo el formato de toda cifra que se lee entera.
extension CompactPesos on Money {
  String get compactPesos {
    final int pesos = (isNegative ? -cents : cents) ~/ 100;
    final String sign = isNegative ? '-' : '';
    if (pesos < _thousand) {
      return '$sign\$ $pesos';
    }
    // Se redondea en la unidad más chica que se va a mostrar y, si el
    // redondeo llega a la siguiente (999.999 → «1.000 mil»), se sube.
    if (pesos < _million) {
      final int thousands = _rounded(pesos, _thousand);
      if (thousands < 1000) {
        return '$sign\$ $thousands mil';
      }
    }
    if (pesos < _billion) {
      final int tenths = _rounded(pesos * 10, _million);
      if (tenths < 10000) {
        return '$sign\$ ${_withTenth(tenths)} M';
      }
    }
    return '$sign\$ ${_withTenth(_rounded(pesos * 10, _billion))} mil M';
  }
}

const int _thousand = 1000;
const int _million = 1000000;
const int _billion = 1000000000;

/// [value] / [unit], redondeado a la mitad hacia arriba, en enteros.
int _rounded(int value, int unit) => (value + unit ~/ 2) ~/ unit;

/// `15` → `1,5`; `10` → `1`.
String _withTenth(int tenths) {
  final int whole = tenths ~/ 10;
  final int tenth = tenths % 10;
  return tenth == 0 ? '$whole' : '$whole,$tenth';
}

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/money/money_format.dart';

void main() {
  group('el formato se arma desde los centavos', () {
    test('miles con punto y centavos con coma', () {
      expect(Money.ofCents(123456789).inPesos, r'$ 1.234.567,89');
    });

    test('siempre dos decimales, aunque sean cero', () {
      expect(Money.ofCents(123400).inPesos, r'$ 1.234,00');
      expect(Money.zero.inPesos, r'$ 0,00');
    });

    test('menos de un peso', () {
      expect(Money.ofCents(5).inPesos, r'$ 0,05');
      expect(Money.ofCents(50).inPesos, r'$ 0,50');
    });

    test('el signo va delante de todo', () {
      expect(Money.ofCents(-123400).inPesos, r'-$ 1.234,00');
      expect(Money.ofCents(-5).inPesos, r'-$ 0,05');
    });

    test('justo en el borde de cada grupo de miles', () {
      expect(Money.ofCents(99999).inPesos, r'$ 999,99');
      expect(Money.ofCents(100000).inPesos, r'$ 1.000,00');
      expect(Money.ofCents(99999999).inPesos, r'$ 999.999,99');
      expect(Money.ofCents(100000000).inPesos, r'$ 1.000.000,00');
    });

    test('el tope se puede mostrar', () {
      expect(Money.ofCents(Money.maxCents).inPesos, r'$ 10.000.000.000.000,00');
    });
  });
}

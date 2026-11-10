import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/money/compact_pesos.dart';
import 'package:mobile/core/money/money.dart';

/// El formato corto de los ejes de los gráficos, armado desde los centavos
/// sin pasar por `double`. La cifra exacta sigue siendo `inPesos`.
void main() {
  String compact(int pesos) => Money.ofUnits(pesos).compactPesos;

  test('menos de mil: los pesos enteros', () {
    expect(compact(0), r'$ 0');
    expect(compact(999), r'$ 999');
  });

  test('miles, redondeados al mil', () {
    expect(compact(1000), r'$ 1 mil');
    expect(compact(12345), r'$ 12 mil');
    expect(compact(12500), r'$ 13 mil');
    expect(compact(850000), r'$ 850 mil');
  });

  test('millones, con un decimal y coma', () {
    expect(compact(1000000), r'$ 1 M');
    expect(compact(1500000), r'$ 1,5 M');
    expect(compact(1549999), r'$ 1,5 M');
    expect(compact(1550000), r'$ 1,6 M');
    expect(compact(999949999), r'$ 999,9 M');
  });

  test('miles de millones', () {
    expect(compact(2345678901), r'$ 2,3 mil M');
    expect(compact(10000000000), r'$ 10 mil M');
  });

  test('redondear no deja «1.000 mil»: sube a la unidad siguiente', () {
    expect(compact(999999), r'$ 1 M');
    expect(compact(999999999), r'$ 1 mil M');
  });

  test('el signo delante, como en inPesos: es el lado del asiento', () {
    expect(compact(-1500000), r'-$ 1,5 M');
    expect(compact(-500), r'-$ 500');
  });

  test('centavos sueltos no cambian el corto', () {
    expect(Money.ofCents(150000099).compactPesos, r'$ 1,5 M');
  });

  test('exacto en el tope, donde un double ya perdería la cuenta', () {
    // 10 billones de pesos menos un centavo: con double, el redondeo en el
    // camino podría dar otro número.
    expect(Money.ofCents(Money.maxCents - 1).compactPesos, r'$ 10000 mil M');
  });
}

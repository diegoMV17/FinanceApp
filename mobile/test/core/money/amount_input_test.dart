import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/money/amount_input.dart';
import 'package:mobile/core/money/money.dart';

/// Lo que el usuario escribe como monto, en pesos colombianos: punto de miles
/// y coma decimal. Pasa a centavos sin tocar un `double`.
void main() {
  Matcher fails(AmountProblem problem) => throwsA(
    isA<AmountInputException>().having(
      (AmountInputException e) => e.problem,
      'problem',
      problem,
    ),
  );

  test('pesos enteros', () {
    expect(parseAmount('12345'), Money.ofCents(1234500));
    expect(parseAmount('1'), Money.ofCents(100));
  });

  test('con puntos de miles', () {
    expect(parseAmount('12.345'), Money.ofCents(1234500));
    expect(parseAmount('1.234.567'), Money.ofCents(123456700));
  });

  test('con centavos tras la coma', () {
    expect(parseAmount('12.345,67'), Money.ofCents(1234567));
    expect(parseAmount('0,5'), Money.ofCents(50));
    expect(parseAmount('0,05'), Money.ofCents(5));
    expect(parseAmount('10,10'), Money.ofCents(1010));
  });

  test('el signo de pesos y los espacios de los lados no estorban', () {
    expect(parseAmount(r' $ 12.345 '), Money.ofCents(1234500));
    expect(parseAmount(r'$12.345,6'), Money.ofCents(1234560));
  });

  test('vacío', () {
    expect(() => parseAmount(''), fails(AmountProblem.empty));
    expect(() => parseAmount('   '), fails(AmountProblem.empty));
    expect(() => parseAmount(r'$'), fails(AmountProblem.empty));
  });

  test('lo que no es un monto en pesos', () {
    for (final String text in <String>[
      'abc',
      '12.5', // el punto es de miles: ¿doce mil quinientos o doce y medio?
      '12.34.567',
      '1.2345',
      '12,345', // tres decimales
      '12,',
      ',5',
      '1 234',
      '-500', // el signo lo decide el servidor, no quien escribe
      '+500',
      '١٢٣', // dígitos que no son 0-9
      '12e3',
    ]) {
      expect(
        () => parseAmount(text),
        fails(AmountProblem.malformed),
        reason: text,
      );
    }
  });

  test('cero no es un monto', () {
    expect(() => parseAmount('0'), fails(AmountProblem.notPositive));
    expect(() => parseAmount('0,00'), fails(AmountProblem.notPositive));
  });

  test('el tope es el del servidor', () {
    expect(parseAmount('10.000.000.000'), Money.ofCents(maxAmountCents));
    expect(
      () => parseAmount('10.000.000.000,01'),
      fails(AmountProblem.tooLarge),
    );
    // Sin desbordar el entero en el camino.
    expect(
      () => parseAmount('99999999999999999999999999'),
      fails(AmountProblem.tooLarge),
    );
  });

  test('ida y vuelta: el texto para editar un monto', () {
    expect(amountText(Money.ofCents(1234567)), '12.345,67');
    expect(amountText(Money.ofCents(1234500)), '12.345');
    expect(amountText(Money.ofCents(5)), '0,05');
    for (final int cents in <int>[1, 99, 100, 1234567, maxAmountCents]) {
      expect(parseAmount(amountText(Money.ofCents(cents))).cents, cents);
    }
  });
}

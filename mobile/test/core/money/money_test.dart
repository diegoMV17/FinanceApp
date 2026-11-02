import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/money/money.dart';

void main() {
  group('se construye en centavos enteros', () {
    test('los centavos son el dato', () {
      expect(Money.ofCents(123456).cents, 123456);
    });

    test('los pesos enteros son una comodidad para la interfaz', () {
      expect(Money.ofUnits(1234).cents, 123400);
    });

    test('cero', () {
      expect(Money.zero.cents, 0);
      expect(Money.zero.isZero, isTrue);
    });

    test('el signo dice el lado del asiento, no si es bueno o malo', () {
      expect(Money.ofCents(-500).isNegative, isTrue);
      expect(Money.ofCents(-500).isPositive, isFalse);
      expect(Money.ofCents(500).isPositive, isTrue);
      expect(Money.zero.isNegative, isFalse);
      expect(Money.zero.isPositive, isFalse);
    });
  });

  group('no se puede construir un monto imposible', () {
    test('el tope se acepta', () {
      expect(Money.ofCents(Money.maxCents).cents, Money.maxCents);
      expect(Money.ofCents(-Money.maxCents).cents, -Money.maxCents);
    });

    test('pasado el tope, no', () {
      expect(
        () => Money.ofCents(Money.maxCents + 1),
        throwsA(isA<MoneyOverflow>()),
      );
      expect(
        () => Money.ofCents(-Money.maxCents - 1),
        throwsA(isA<MoneyOverflow>()),
      );
    });

    test('el entero más pequeño tampoco: su valor absoluto es él mismo', () {
      // En Dart, (-9223372036854775808).abs() vuelve a ser negativo. Una
      // comprobación escrita con abs() lo dejaría pasar.
      expect(
        () => Money.ofCents(-9223372036854775808),
        throwsA(isA<MoneyOverflow>()),
      );
    });

    test('ofUnits también se queda dentro del tope', () {
      expect(
        () => Money.ofUnits(Money.maxCents),
        throwsA(isA<MoneyOverflow>()),
      );
    });
  });

  group('aritmética', () {
    test('suma y resta', () {
      expect(Money.ofCents(300).plus(Money.ofCents(250)).cents, 550);
      expect(Money.ofCents(300).minus(Money.ofCents(250)).cents, 50);
      expect(Money.ofCents(300).minus(Money.ofCents(800)).cents, -500);
    });

    test('negar y multiplicar por un entero', () {
      expect(Money.ofCents(300).negated().cents, -300);
      expect(Money.ofCents(-300).negated().cents, 300);
      expect(Money.ofCents(250).times(4).cents, 1000);
      expect(Money.ofCents(250).times(0).cents, 0);
      expect(Money.ofCents(250).times(-2).cents, -500);
    });

    test('sumar una lista', () {
      expect(
        Money.sum(<Money>[
          Money.ofCents(100),
          Money.ofCents(-250),
          Money.ofCents(50),
        ]).cents,
        -100,
      );
      expect(Money.sum(const <Money>[]), Money.zero);
    });

    test('una suma que se sale del tope falla, no da la vuelta', () {
      final Money tope = Money.ofCents(Money.maxCents);

      expect(() => tope.plus(Money.ofCents(1)), throwsA(isA<MoneyOverflow>()));
      expect(
        () => Money.ofCents(-Money.maxCents).minus(Money.ofCents(1)),
        throwsA(isA<MoneyOverflow>()),
      );
    });

    test('una multiplicación que da la vuelta falla', () {
      // Dart no tiene multiplyExact. Con este factor el producto desborda el
      // int de 64 bits y lo que queda —255.926.290.448.384 centavos— cabe
      // dentro del tope: mirar solo el valor del resultado lo dejaría pasar
      // por bueno, y el monto sería basura. El tope es 1e15, así que
      // 1e15 * 18447 se pasa de 2^64 por poco.
      expect(
        () => Money.ofCents(Money.maxCents).times(18447),
        throwsA(isA<MoneyOverflow>()),
      );
      expect(
        () => Money.ofCents(Money.maxCents).times(100000),
        throwsA(isA<MoneyOverflow>()),
      );
    });
  });

  group('igualdad y orden', () {
    test('dos montos con los mismos centavos son el mismo', () {
      expect(Money.ofCents(42), Money.ofCents(42));
      expect(Money.ofCents(42).hashCode, Money.ofCents(42).hashCode);
      expect(Money.ofCents(42), isNot(Money.ofCents(43)));
    });

    test('se ordenan por centavos', () {
      final List<Money> amounts = <Money>[
        Money.ofCents(100),
        Money.ofCents(-50),
        Money.zero,
      ]..sort();

      expect(amounts.map((Money m) => m.cents), <int>[-50, 0, 100]);
    });
  });
}

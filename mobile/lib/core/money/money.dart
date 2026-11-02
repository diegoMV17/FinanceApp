/// Un monto de plata, guardado en centavos enteros.
///
/// Nunca coma flotante para la plata: `0.1 + 0.2` no es `0.3`, y después de
/// unos cientos de movimientos el saldo se desvía de formas imposibles de
/// explicarle a quien lee la pantalla. Es el espejo del `Money` del servidor,
/// que es la única verdad: aquí no se calculan saldos, solo se arma lo que se
/// escribe y se muestra lo que llega.
///
/// El signo dice de qué lado del asiento está el monto, no si es bueno o malo.
class Money implements Comparable<Money> {
  const Money._(this.cents);

  /// Diez billones de pesos. Un monto más grande que esto en finanzas
  /// personales es un error de digitación, y el tope es lo que permite
  /// detectar un desbordamiento antes de que el entero de 64 bits dé la
  /// vuelta en silencio: Dart no tiene `Math.addExact`.
  static const int maxCents = 1000000000000000;

  static const Money zero = Money._(0);

  final int cents;

  factory Money.ofCents(int cents) {
    // Escrito sin abs() a propósito: el valor absoluto del entero más pequeño
    // vuelve a ser negativo, así que `cents.abs() > maxCents` lo dejaría pasar.
    if (cents < -maxCents || cents > maxCents) {
      throw MoneyOverflow(cents);
    }
    return Money._(cents);
  }

  /// Comodidad para los tests y para la interfaz, que habla en pesos enteros.
  factory Money.ofUnits(int units) {
    if (units < -_maxUnits || units > _maxUnits) {
      throw MoneyOverflow(units);
    }
    return Money.ofCents(units * _centsPerUnit);
  }

  static const int _centsPerUnit = 100;
  static const int _maxUnits = maxCents ~/ _centsPerUnit;

  Money plus(Money other) => Money.ofCents(cents + other.cents);

  Money minus(Money other) => Money.ofCents(cents - other.cents);

  Money negated() => Money.ofCents(-cents);

  Money times(int factor) {
    final int result = cents * factor;
    // La multiplicación puede dar la vuelta y caer dentro del tope: el único
    // modo de saberlo es deshacerla.
    if (cents != 0 && result ~/ cents != factor) {
      throw MoneyOverflow(result);
    }
    return Money.ofCents(result);
  }

  bool get isZero => cents == 0;

  bool get isPositive => cents > 0;

  bool get isNegative => cents < 0;

  static Money sum(Iterable<Money> amounts) {
    Money total = zero;
    for (final Money amount in amounts) {
      total = total.plus(amount);
    }
    return total;
  }

  @override
  int compareTo(Money other) => cents.compareTo(other.cents);

  @override
  bool operator ==(Object other) => other is Money && other.cents == cents;

  @override
  int get hashCode => cents.hashCode;

  @override
  String toString() => 'Money($cents)';
}

/// Un monto que no cabe: pasado el tope, o una operación que daría la vuelta.
class MoneyOverflow implements Exception {
  const MoneyOverflow(this.cents);

  final int cents;

  @override
  String toString() => 'MoneyOverflow: $cents';
}

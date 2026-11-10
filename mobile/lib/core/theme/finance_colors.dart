import 'package:flutter/material.dart';

/// El papel de una cifra en pantalla, que decide su color.
///
/// **El color va por papel, nunca por el signo.** El signo de un monto es el
/// lado del asiento, no si es bueno o malo (igual que en el servidor): un
/// saldo negativo en «Debo» es lo normal. Por eso una cifra se pinta como
/// ingreso o gasto porque **es** un ingreso o un gasto, y lo demás va en el
/// color del texto.
enum MoneyRole {
  /// El color del texto: saldos, totales, lo que no es ni ingreso ni gasto.
  neutral,

  /// Plata que entra: ingresos del mes, lo que me deben.
  income,

  /// Plata que sale: gastos del mes, lo que debo.
  expense,
}

/// Los colores por papel, en claro y en oscuro, más el de los avisos. Un test
/// exige que cada uno se lea (WCAG AA) contra la pantalla y las tarjetas.
@immutable
class FinanceColors extends ThemeExtension<FinanceColors> {
  const FinanceColors({
    required this.income,
    required this.expense,
    required this.warning,
  });

  static const FinanceColors light = FinanceColors(
    income: Color(0xFF146C43),
    expense: Color(0xFFB3261E),
    warning: Color(0xFF8A5300),
  );

  static const FinanceColors dark = FinanceColors(
    income: Color(0xFF7DDBA8),
    expense: Color(0xFFFFB4AB),
    warning: Color(0xFFF2BE6B),
  );

  final Color income;
  final Color expense;

  /// «Cerca del límite» y lo que pide atención sin ser un error.
  final Color warning;

  Color forRole(MoneyRole role, ColorScheme scheme) => switch (role) {
    MoneyRole.neutral => scheme.onSurface,
    MoneyRole.income => income,
    MoneyRole.expense => expense,
  };

  @override
  FinanceColors copyWith({Color? income, Color? expense, Color? warning}) =>
      FinanceColors(
        income: income ?? this.income,
        expense: expense ?? this.expense,
        warning: warning ?? this.warning,
      );

  @override
  FinanceColors lerp(FinanceColors? other, double t) {
    if (other == null) {
      return this;
    }
    return FinanceColors(
      income: Color.lerp(income, other.income, t)!,
      expense: Color.lerp(expense, other.expense, t)!,
      warning: Color.lerp(warning, other.warning, t)!,
    );
  }
}

extension FinanceColorsOf on BuildContext {
  /// Los colores por papel del tema en uso.
  FinanceColors get financeColors =>
      Theme.of(this).extension<FinanceColors>() ?? FinanceColors.light;
}

import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/theme/app_theme.dart';
import 'package:mobile/core/theme/finance_colors.dart';

/// El tema: sobrio, con el verde de la app, y legible. Un color por papel que
/// no se lee contra su fondo es un dato que no se ve.
void main() {
  double luminance(Color c) {
    double channel(double v) => v <= 0.03928
        ? v / 12.92
        : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
    return 0.2126 * channel(c.r) +
        0.7152 * channel(c.g) +
        0.0722 * channel(c.b);
  }

  double contrast(Color a, Color b) {
    final double la = luminance(a);
    final double lb = luminance(b);
    return (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05);
  }

  for (final Brightness brightness in Brightness.values) {
    group('tema ${brightness.name}', () {
      final ThemeData theme = appTheme(brightness);
      final FinanceColors colors = theme.extension<FinanceColors>()!;
      final ColorScheme scheme = theme.colorScheme;

      test('nace del verde de la app', () {
        expect(theme.colorScheme.brightness, brightness);
        expect(brandSeed, const Color(0xFF1B6B50));
      });

      test('con Inter, la fuente empaquetada', () {
        expect(theme.textTheme.bodyMedium!.fontFamily, 'Inter');
        expect(theme.textTheme.headlineMedium!.fontFamily, 'Inter');
      });

      test('cada color por papel se lee (WCAG AA, 4,5:1) contra la '
          'pantalla y contra las tarjetas', () {
        for (final MoneyRole role in MoneyRole.values) {
          final Color color = colors.forRole(role, scheme);
          for (final (String name, Color background) in <(String, Color)>[
            ('surface', scheme.surface),
            ('surfaceContainerLow', scheme.surfaceContainerLow),
          ]) {
            expect(
              contrast(color, background),
              greaterThanOrEqualTo(4.5),
              reason: '${role.name} sobre $name',
            );
          }
        }
        expect(
          contrast(colors.warning, scheme.surface),
          greaterThanOrEqualTo(4.5),
        );
      });

      test('ingreso y gasto no se confunden', () {
        expect(
          colors.forRole(MoneyRole.income, scheme),
          isNot(colors.forRole(MoneyRole.expense, scheme)),
        );
      });
    });
  }

  test('la plata va con cifras tabulares: las columnas no bailan', () {
    final TextStyle style = moneyStyle(const TextStyle(fontSize: 14));

    expect(style.fontFeatures, contains(const FontFeature.tabularFigures()));
    expect(style.fontSize, 14);
  });

  test(
    'neutral es el color del texto: la cifra no dice si es buena o mala',
    () {
      final ThemeData theme = appTheme(Brightness.light);
      expect(
        theme.extension<FinanceColors>()!.forRole(
          MoneyRole.neutral,
          theme.colorScheme,
        ),
        theme.colorScheme.onSurface,
      );
    },
  );
}

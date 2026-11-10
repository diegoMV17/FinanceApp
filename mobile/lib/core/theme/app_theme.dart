import 'package:flutter/material.dart';

import 'finance_colors.dart';

/// El verde de la app: la semilla de la paleta, el fondo del ícono y del
/// arranque (`android/.../values/colors.xml`).
const Color brandSeed = Color(0xFF1B6B50);

const String _font = 'Inter';

/// El tema: sobrio, con el verde de la app, mucho aire y tarjetas suaves. El
/// claro o el oscuro lo decide el teléfono.
ThemeData appTheme(Brightness brightness) {
  final ColorScheme scheme = ColorScheme.fromSeed(
    seedColor: brandSeed,
    brightness: brightness,
  );
  final ThemeData base = ThemeData(
    colorScheme: scheme,
    fontFamily: _font,
    brightness: brightness,
  );
  final TextTheme text = base.textTheme;
  final RoundedRectangleBorder rounded = RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(16),
  );
  return base.copyWith(
    scaffoldBackgroundColor: scheme.surface,
    textTheme: text.copyWith(
      headlineMedium: text.headlineMedium?.copyWith(
        fontWeight: FontWeight.w700,
        letterSpacing: -0.5,
      ),
      titleLarge: text.titleLarge?.copyWith(fontWeight: FontWeight.w600),
      titleMedium: text.titleMedium?.copyWith(fontWeight: FontWeight.w600),
      labelLarge: text.labelLarge?.copyWith(fontWeight: FontWeight.w600),
    ),
    appBarTheme: AppBarTheme(
      backgroundColor: scheme.surface,
      foregroundColor: scheme.onSurface,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      titleTextStyle: text.titleLarge?.copyWith(
        fontFamily: _font,
        fontWeight: FontWeight.w700,
        color: scheme.onSurface,
      ),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: scheme.surfaceContainerLow,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: scheme.surfaceContainerHighest.withValues(alpha: 0.5),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: scheme.primary, width: 2),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(52),
        shape: rounded,
        textStyle: const TextStyle(
          fontFamily: _font,
          fontWeight: FontWeight.w600,
          fontSize: 16,
        ),
      ),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      shape: rounded,
      elevation: 2,
    ),
    chipTheme: base.chipTheme.copyWith(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      side: BorderSide(color: scheme.outlineVariant),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: scheme.surfaceContainer,
      indicatorColor: scheme.secondaryContainer,
      elevation: 0,
      labelTextStyle: WidgetStatePropertyAll<TextStyle>(
        TextStyle(
          fontFamily: _font,
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: scheme.onSurface,
        ),
      ),
    ),
    listTileTheme: const ListTileThemeData(
      contentPadding: EdgeInsets.symmetric(horizontal: 16),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      shape: rounded,
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: scheme.surfaceContainerLow,
      showDragHandle: true,
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      linearTrackColor: scheme.surfaceContainerHighest,
      linearMinHeight: 8,
      borderRadius: BorderRadius.circular(4),
    ),
    extensions: <ThemeExtension<dynamic>>[
      if (brightness == Brightness.light)
        FinanceColors.light
      else
        FinanceColors.dark,
    ],
  );
}

/// El estilo de una cifra de plata: **cifras tabulares**, para que en una
/// columna los dígitos queden alineados y un saldo que cambia no haga bailar
/// el texto.
TextStyle moneyStyle(TextStyle base) => base.copyWith(
  fontFeatures: const <FontFeature>[FontFeature.tabularFigures()],
);

import 'package:flutter/material.dart';

import '../money/money.dart';
import '../money/money_format.dart';
import '../theme/app_theme.dart';
import '../theme/finance_colors.dart';

/// Una cifra de plata: escrita desde los centavos (`inPesos`), con cifras
/// tabulares y el color de su papel. **Nunca el color por el signo.**
class MoneyText extends StatelessWidget {
  const MoneyText(
    this.amount, {
    this.role = MoneyRole.neutral,
    this.style,
    super.key,
  });

  final Money amount;
  final MoneyRole role;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final TextStyle base = style ?? theme.textTheme.bodyLarge!;
    return Text(
      amount.inPesos,
      style: moneyStyle(
        base,
      ).copyWith(color: context.financeColors.forRole(role, theme.colorScheme)),
    );
  }
}

/// Una tarjeta con su título: la forma de agrupar de todas las pantallas.
class SectionCard extends StatelessWidget {
  const SectionCard({
    required this.child,
    this.title,
    this.trailing,
    this.padding = const EdgeInsets.all(16),
    super.key,
  });

  final String? title;
  final Widget? trailing;
  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final String? heading = title;
    return Card(
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            if (heading != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        heading,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    ?trailing,
                  ],
                ),
              ),
            child,
          ],
        ),
      ),
    );
  }
}

/// Una cifra con su etiqueta y un icono en su color: ingresos y gastos del
/// mes, por ejemplo.
class StatTile extends StatelessWidget {
  const StatTile({
    required this.label,
    required this.amount,
    required this.role,
    required this.icon,
    super.key,
  });

  final String label;
  final Money amount;
  final MoneyRole role;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Color color = context.financeColors.forRole(role, theme.colorScheme);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            CircleAvatar(
              radius: 16,
              backgroundColor: color.withValues(alpha: 0.12),
              child: Icon(icon, size: 18, color: color),
            ),
            const SizedBox(height: 12),
            Text(label, style: theme.textTheme.labelMedium),
            const SizedBox(height: 2),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: MoneyText(
                amount,
                role: role,
                style: theme.textTheme.titleMedium,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Lo que se ve cuando no hay nada: qué falta y, si hay, cómo empezar.
class EmptyState extends StatelessWidget {
  const EmptyState({
    required this.icon,
    required this.title,
    this.message,
    this.actionLabel,
    this.onAction,
    super.key,
  });

  final IconData icon;
  final String title;
  final String? message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final String? detail = message;
    final String? label = actionLabel;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 40),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          CircleAvatar(
            radius: 36,
            backgroundColor: theme.colorScheme.primaryContainer,
            child: Icon(
              icon,
              size: 36,
              color: theme.colorScheme.onPrimaryContainer,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            title,
            style: theme.textTheme.titleMedium,
            textAlign: TextAlign.center,
          ),
          if (detail != null) ...<Widget>[
            const SizedBox(height: 8),
            Text(
              detail,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
          ],
          if (label != null && onAction != null) ...<Widget>[
            const SizedBox(height: 20),
            FilledButton.tonal(onPressed: onAction, child: Text(label)),
          ],
        ],
      ),
    );
  }
}

/// Mientras carga: bloques del tamaño de lo que viene, sin cifras inventadas.
class LoadingBlocks extends StatelessWidget {
  const LoadingBlocks({this.heights = const <double>[120, 88, 220], super.key});

  final List<double> heights;

  @override
  Widget build(BuildContext context) {
    final Color color = Theme.of(context).colorScheme.surfaceContainerHigh;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: <Widget>[
        for (final double height in heights)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Container(
              height: height,
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(20),
              ),
            ),
          ),
      ],
    );
  }
}

/// Un círculo con las iniciales de un nombre: para las personas.
class InitialsAvatar extends StatelessWidget {
  const InitialsAvatar(this.name, {super.key});

  final String name;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return CircleAvatar(
      backgroundColor: scheme.secondaryContainer,
      foregroundColor: scheme.onSecondaryContainer,
      child: Text(initialsOf(name)),
    );
  }
}

/// Hasta dos iniciales, en mayúscula: «Juan Pérez» → «JP».
String initialsOf(String name) {
  final List<String> words = name
      .trim()
      .split(RegExp(r'\s+'))
      .where((String w) => w.isNotEmpty)
      .toList();
  if (words.isEmpty) {
    return '?';
  }
  final String first = String.fromCharCode(words.first.runes.first);
  if (words.length == 1) {
    return first.toUpperCase();
  }
  final String last = String.fromCharCode(words.last.runes.first);
  return '$first$last'.toUpperCase();
}

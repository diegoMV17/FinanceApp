import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../../core/dates/time_label.dart';
import '../../../core/ledger/month_summary.dart';
import '../../../core/money/compact_pesos.dart';
import '../../../core/money/money.dart';
import '../../../core/money/money_format.dart';
import '../../../core/theme/finance_colors.dart';
import '../../../core/widgets/components.dart';

/// La plata que es de verdad del dueño, a fin de cada mes del último año.
///
/// Los puntos y sus valores los da el servidor; aquí solo se dibujan. Para
/// ubicarlos en la pantalla fl_chart pide `double`, así que la altura de cada
/// punto es su número de centavos como `double` (exacto: el tope de `Money`
/// cabe sin pérdida en los 53 bits de la mantisa). **Ninguna cifra que se lee
/// sale de ese `double`:** el eje se marca solo en el punto más bajo y en el
/// más alto, con el `Money` de esos puntos, y el toque muestra el `Money` del
/// punto tocado.
class OverTimeChart extends StatelessWidget {
  const OverTimeChart({required this.points, super.key});

  final List<MonthSummary> points;

  @override
  Widget build(BuildContext context) {
    if (points.isEmpty) {
      return const SizedBox.shrink();
    }
    final ThemeData theme = Theme.of(context);
    final ColorScheme colors = theme.colorScheme;
    final Money low = points
        .map((MonthSummary p) => p.moneyReallyMine)
        .reduce((Money a, Money b) => a.compareTo(b) <= 0 ? a : b);
    final Money high = points
        .map((MonthSummary p) => p.moneyReallyMine)
        .reduce((Money a, Money b) => a.compareTo(b) >= 0 ? a : b);
    final TextStyle? axis = theme.textTheme.labelSmall?.copyWith(
      color: colors.onSurfaceVariant,
    );
    return AspectRatio(
      aspectRatio: 1.7,
      child: LineChart(
        LineChartData(
          minY: low.cents.toDouble(),
          maxY: high == low ? low.cents + 1 : high.cents.toDouble(),
          gridData: FlGridData(
            drawVerticalLine: false,
            horizontalInterval: high == low
                ? 1
                : (high.cents - low.cents).toDouble(),
            getDrawingHorizontalLine: (_) =>
                FlLine(color: colors.outlineVariant, strokeWidth: 1),
          ),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            rightTitles: const AxisTitles(),
            topTitles: const AxisTitles(),
            leftTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 64,
                getTitlesWidget: (double y, TitleMeta meta) {
                  // Solo los extremos, con el Money de esos puntos.
                  final Money? mark = y == low.cents.toDouble()
                      ? low
                      : (y == high.cents.toDouble() ? high : null);
                  if (mark == null) {
                    return const SizedBox.shrink();
                  }
                  return SideTitleWidget(
                    meta: meta,
                    child: Text(mark.compactPesos, style: axis),
                  );
                },
              ),
            ),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                interval: 1,
                getTitlesWidget: (double x, TitleMeta meta) {
                  final int i = x.round();
                  if (i < 0 || i >= points.length || i != x) {
                    return const SizedBox.shrink();
                  }
                  return SideTitleWidget(
                    meta: meta,
                    child: Text(shortMonth(points[i].asOf.month), style: axis),
                  );
                },
              ),
            ),
          ),
          lineTouchData: LineTouchData(
            touchTooltipData: LineTouchTooltipData(
              getTooltipColor: (_) => colors.inverseSurface,
              getTooltipItems: (List<LineBarSpot> spots) => <LineTooltipItem?>[
                for (final LineBarSpot spot in spots)
                  LineTooltipItem(
                    '${dateLabel(points[spot.spotIndex].asOf)}\n'
                    '${points[spot.spotIndex].moneyReallyMine.inPesos}',
                    TextStyle(
                      color: colors.onInverseSurface,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
              ],
            ),
          ),
          lineBarsData: <LineChartBarData>[
            LineChartBarData(
              spots: <FlSpot>[
                for (int i = 0; i < points.length; i++)
                  FlSpot(
                    i.toDouble(),
                    points[i].moneyReallyMine.cents.toDouble(),
                  ),
              ],
              isCurved: true,
              preventCurveOverShooting: true,
              color: colors.primary,
              barWidth: 3,
              isStrokeCapRound: true,
              dotData: const FlDotData(show: false),
              belowBarData: BarAreaData(
                show: true,
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: <Color>[
                    colors.primary.withValues(alpha: 0.28),
                    colors.primary.withValues(alpha: 0),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Cuánto se gastó en cada categoría del mes: una dona con el total al
/// centro y, debajo, la lista con las cifras, que son las que se leen.
///
/// El tamaño de cada porción es el total en centavos como `double`, solo para
/// dibujarla; las cifras salen del `Money`.
class CategoryChart extends StatelessWidget {
  const CategoryChart({required this.totals, required this.total, super.key});

  final List<CategoryTotal> totals;

  /// Los gastos del mes según el resumen, para el centro de la dona.
  final Money total;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final List<CategoryTotal> spent = totals
        .where((CategoryTotal t) => t.total.isPositive)
        .toList();
    if (spent.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Text(
          'Sin gastos este mes.',
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      );
    }
    final List<Color> palette = _palette(theme.colorScheme);
    return Column(
      children: <Widget>[
        SizedBox(
          height: 180,
          child: Stack(
            alignment: Alignment.center,
            children: <Widget>[
              PieChart(
                PieChartData(
                  sectionsSpace: 3,
                  centerSpaceRadius: 58,
                  sections: <PieChartSectionData>[
                    for (int i = 0; i < spent.length; i++)
                      PieChartSectionData(
                        value: spent[i].total.cents.toDouble(),
                        color: palette[i % palette.length],
                        showTitle: false,
                        radius: 22,
                      ),
                  ],
                ),
              ),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Text('Total', style: theme.textTheme.labelSmall),
                  Text(
                    total.compactPesos,
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: context.financeColors.expense,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        for (int i = 0; i < spent.length; i++)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              children: <Widget>[
                Container(
                  width: 10,
                  height: 10,
                  decoration: BoxDecoration(
                    color: palette[i % palette.length],
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(child: Text(spent[i].category.name)),
                MoneyText(spent[i].total, role: MoneyRole.expense),
              ],
            ),
          ),
      ],
    );
  }

  static List<Color> _palette(ColorScheme colors) => <Color>[
    colors.primary,
    colors.tertiary,
    colors.secondary,
    colors.primaryFixedDim,
    colors.tertiaryFixedDim,
    colors.outline,
  ];
}

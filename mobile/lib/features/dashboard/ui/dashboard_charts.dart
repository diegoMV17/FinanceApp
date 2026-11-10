import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../../core/dates/time_label.dart';
import '../../../core/ledger/month_summary.dart';
import '../../../core/money/money_format.dart';

/// La plata que es de verdad del dueño, a fin de cada mes del último año.
///
/// Los puntos y sus valores los da el servidor; aquí solo se dibujan. Para
/// ubicarlos en la pantalla fl_chart pide `double`, así que la altura de cada
/// punto es su número de centavos como `double` (exacto: el tope de `Money`
/// cabe sin pérdida en los 53 bits de la mantisa). **Ninguna cifra que se lee
/// sale de ese `double`:** la que muestra el toque se arma desde el `Money`
/// original, en centavos.
class OverTimeChart extends StatelessWidget {
  const OverTimeChart({required this.points, super.key});

  final List<MonthSummary> points;

  @override
  Widget build(BuildContext context) {
    if (points.isEmpty) {
      return const SizedBox.shrink();
    }
    final ColorScheme colors = Theme.of(context).colorScheme;
    return AspectRatio(
      aspectRatio: 1.8,
      child: LineChart(
        LineChartData(
          gridData: const FlGridData(show: false),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            leftTitles: const AxisTitles(),
            rightTitles: const AxisTitles(),
            topTitles: const AxisTitles(),
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
                    child: Text(
                      shortMonth(points[i].asOf.month),
                      style: Theme.of(context).textTheme.labelSmall,
                    ),
                  );
                },
              ),
            ),
          ),
          lineTouchData: LineTouchData(
            touchTooltipData: LineTouchTooltipData(
              getTooltipItems: (List<LineBarSpot> spots) => <LineTooltipItem?>[
                for (final LineBarSpot spot in spots)
                  LineTooltipItem(
                    '${dateLabel(points[spot.spotIndex].asOf)}\n'
                    '${points[spot.spotIndex].moneyReallyMine.inPesos}',
                    TextStyle(color: colors.onInverseSurface),
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
              color: colors.primary,
              barWidth: 3,
              dotData: const FlDotData(show: false),
            ),
          ],
        ),
      ),
    );
  }
}

/// Cuánto se gastó en cada categoría del mes: una torta y, debajo, la lista
/// con las cifras, que son las que se leen.
///
/// El tamaño de cada porción es el total en centavos como `double`, solo para
/// dibujarla; las cifras de la lista salen del `Money`.
class CategoryChart extends StatelessWidget {
  const CategoryChart({required this.totals, super.key});

  final List<CategoryTotal> totals;

  @override
  Widget build(BuildContext context) {
    final List<CategoryTotal> spent = totals
        .where((CategoryTotal t) => t.total.isPositive)
        .toList();
    if (spent.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: Text('Sin gastos este mes.'),
      );
    }
    final List<Color> palette = _palette(Theme.of(context).colorScheme);
    return Column(
      children: <Widget>[
        AspectRatio(
          aspectRatio: 1.8,
          child: PieChart(
            PieChartData(
              sectionsSpace: 2,
              centerSpaceRadius: 36,
              sections: <PieChartSectionData>[
                for (int i = 0; i < spent.length; i++)
                  PieChartSectionData(
                    value: spent[i].total.cents.toDouble(),
                    color: palette[i % palette.length],
                    showTitle: false,
                    radius: 40,
                  ),
              ],
            ),
          ),
        ),
        for (int i = 0; i < spent.length; i++)
          ListTile(
            dense: true,
            leading: CircleAvatar(
              radius: 6,
              backgroundColor: palette[i % palette.length],
            ),
            title: Text(spent[i].category.name),
            trailing: Text(spent[i].total.inPesos),
          ),
      ],
    );
  }

  static List<Color> _palette(ColorScheme colors) => <Color>[
    colors.primary,
    colors.tertiary,
    colors.secondary,
    colors.error,
    colors.primaryContainer,
    colors.tertiaryContainer,
    colors.secondaryContainer,
  ];
}

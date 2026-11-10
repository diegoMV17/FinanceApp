import 'calendar_date.dart';

/// Cuándo se leyó una copia, dicho para el dueño, en la hora del teléfono:
/// «hoy a las 09:05» o «el 08/11/2026 a las 21:30». Armado a mano, sin
/// `intl`.
String readAtLabel(DateTime readAt, DateTime now) {
  final DateTime local = readAt.toLocal();
  final DateTime today = now.toLocal();
  final String time = '${_two(local.hour)}:${_two(local.minute)}';
  final bool sameDay =
      local.year == today.year &&
      local.month == today.month &&
      local.day == today.day;
  if (sameDay) {
    return 'hoy a las $time';
  }
  return 'el ${_two(local.day)}/${_two(local.month)}/${local.year} a las $time';
}

/// `07/03/2026`.
String dateLabel(CalendarDate date) =>
    '${_two(date.day)}/${_two(date.month)}/${date.year}';

/// `noviembre de 2026`.
String monthLabel(YearMonth month) =>
    '${_months[month.month - 1]} de ${month.year}';

/// `ene`, `feb`... para los ejes de los gráficos.
String shortMonth(int month) => _months[month - 1].substring(0, 3);

const List<String> _months = <String>[
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];

String _two(int value) => value.toString().padLeft(2, '0');

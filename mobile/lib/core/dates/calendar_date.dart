/// Una fecha de calendario: año, mes y día, sin hora ni zona.
///
/// Un movimiento lleva la fecha del teléfono en su zona, nunca un instante
/// pasado a fecha en UTC: a las 23:30 del 31 de octubre en Bogotá, para UTC ya
/// es noviembre, y el gasto caería en el mes equivocado. Por eso solo se
/// construye desde una hora **local**; una en UTC se rechaza.
///
/// Viaja como `yyyy-MM-dd`, que es lo que el servidor lee como `LocalDate`.
class CalendarDate implements Comparable<CalendarDate> {
  factory CalendarDate(int year, int month, int day) {
    if (year < 1 || year > 9999) {
      throw ArgumentError.value(year, 'year', 'fuera de 1..9999');
    }
    if (month < 1 || month > 12) {
      throw ArgumentError.value(month, 'month', 'fuera de 1..12');
    }
    if (day < 1 || day > _daysIn(year, month)) {
      throw ArgumentError.value(day, 'day', 'no existe en ese mes');
    }
    return CalendarDate._(year, month, day);
  }

  const CalendarDate._(this.year, this.month, this.day);

  /// Lanza [FormatException] si no es exactamente `yyyy-MM-dd` y una fecha
  /// que existe.
  factory CalendarDate.parse(String text) {
    final RegExpMatch? match = _pattern.firstMatch(text);
    if (match == null) {
      throw const FormatException('una fecha es yyyy-MM-dd');
    }
    try {
      return CalendarDate(
        int.parse(match[1]!),
        int.parse(match[2]!),
        int.parse(match[3]!),
      );
    } on ArgumentError {
      throw const FormatException('esa fecha no existe');
    }
  }

  /// El día de una hora local. Una hora en UTC se rechaza: su día sería el de
  /// otra zona.
  factory CalendarDate.ofLocal(DateTime local) {
    if (local.isUtc) {
      throw ArgumentError.value(
        local,
        'local',
        'una fecha de calendario sale de la hora local, no de UTC',
      );
    }
    return CalendarDate(local.year, local.month, local.day);
  }

  /// Hoy, según el reloj del teléfono en su zona. [clock], solo para tests.
  factory CalendarDate.today([DateTime Function() clock = DateTime.now]) =>
      CalendarDate.ofLocal(clock());

  static final RegExp _pattern = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$');

  final int year;
  final int month;
  final int day;

  YearMonth get yearMonth => YearMonth(year, month);

  bool operator <(CalendarDate other) => compareTo(other) < 0;

  bool operator >(CalendarDate other) => compareTo(other) > 0;

  @override
  int compareTo(CalendarDate other) => _ordinal - other._ordinal;

  int get _ordinal => (year * 12 + month) * 31 + day;

  @override
  bool operator ==(Object other) =>
      other is CalendarDate &&
      other.year == year &&
      other.month == month &&
      other.day == day;

  @override
  int get hashCode => Object.hash(year, month, day);

  @override
  String toString() => '${_pad(year, 4)}-${_pad(month, 2)}-${_pad(day, 2)}';
}

/// Un mes de un año: el de un presupuesto. Viaja como `yyyy-MM`, el patrón
/// que pide el contrato.
class YearMonth implements Comparable<YearMonth> {
  factory YearMonth(int year, int month) {
    if (year < 1 || year > 9999) {
      throw ArgumentError.value(year, 'year', 'fuera de 1..9999');
    }
    if (month < 1 || month > 12) {
      throw ArgumentError.value(month, 'month', 'fuera de 1..12');
    }
    return YearMonth._(year, month);
  }

  const YearMonth._(this.year, this.month);

  /// Lanza [FormatException] si no es exactamente `yyyy-MM`.
  factory YearMonth.parse(String text) {
    final RegExpMatch? match = _pattern.firstMatch(text);
    if (match == null) {
      throw const FormatException('un mes es yyyy-MM');
    }
    return YearMonth(int.parse(match[1]!), int.parse(match[2]!));
  }

  static final RegExp _pattern = RegExp(r'^(\d{4})-(0[1-9]|1[0-2])$');

  final int year;
  final int month;

  CalendarDate get firstDay => CalendarDate(year, month, 1);

  CalendarDate get lastDay => CalendarDate(year, month, _daysIn(year, month));

  YearMonth minusMonths(int months) {
    final int index = year * 12 + (month - 1) - months;
    return YearMonth(index ~/ 12, index % 12 + 1);
  }

  @override
  int compareTo(YearMonth other) =>
      (year * 12 + month) - (other.year * 12 + other.month);

  @override
  bool operator ==(Object other) =>
      other is YearMonth && other.year == year && other.month == month;

  @override
  int get hashCode => Object.hash(year, month);

  @override
  String toString() => '${_pad(year, 4)}-${_pad(month, 2)}';
}

int _daysIn(int year, int month) {
  const List<int> days = <int>[31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  final bool leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
  return month == 2 && leap ? 29 : days[month - 1];
}

String _pad(int value, int width) => value.toString().padLeft(width, '0');

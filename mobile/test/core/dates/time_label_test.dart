import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/dates/time_label.dart';

void main() {
  final DateTime now = DateTime(2026, 11, 9, 15, 30);

  test('de hoy: solo la hora, en la del teléfono', () {
    expect(readAtLabel(DateTime(2026, 11, 9, 9, 5), now), 'hoy a las 09:05');
  });

  test('de otro día: el día y la hora', () {
    expect(
      readAtLabel(DateTime(2026, 11, 8, 21, 30), now),
      'el 08/11/2026 a las 21:30',
    );
  });

  test('un instante en UTC se muestra en la hora del teléfono', () {
    final DateTime utc = DateTime.utc(2026, 11, 9, 14);
    final DateTime local = utc.toLocal();

    expect(
      readAtLabel(utc, now),
      contains(
        '${local.hour.toString().padLeft(2, '0')}:'
        '${local.minute.toString().padLeft(2, '0')}',
      ),
    );
  });

  test('una fecha de calendario como la lee una persona', () {
    expect(dateLabel(CalendarDate(2026, 3, 7)), '07/03/2026');
  });

  test('el nombre corto de cada mes, en español', () {
    expect(shortMonth(1), 'ene');
    expect(shortMonth(12), 'dic');
    expect(monthLabel(YearMonth(2026, 11)), 'noviembre de 2026');
  });
}

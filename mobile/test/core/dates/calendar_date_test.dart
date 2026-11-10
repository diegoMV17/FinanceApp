import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';

/// Un movimiento lleva la fecha de calendario del teléfono, en su zona; nunca
/// un instante convertido a fecha en UTC. A las 23:30 del 31 de octubre en
/// Bogotá, para UTC ya es noviembre.
void main() {
  group('CalendarDate', () {
    test('viaja como yyyy-MM-dd, con ceros', () {
      expect(CalendarDate(2026, 3, 7).toString(), '2026-03-07');
      expect(CalendarDate(987, 12, 31).toString(), '0987-12-31');
    });

    test('se lee de la misma forma, y nada más', () {
      expect(CalendarDate.parse('2026-11-02'), CalendarDate(2026, 11, 2));
      for (final String text in <String>[
        '',
        '2026-11-2',
        '2026/11/02',
        '2026-11-02T00:00:00',
        ' 2026-11-02',
        '2026-13-01',
        '2026-02-30',
      ]) {
        expect(
          () => CalendarDate.parse(text),
          throwsFormatException,
          reason: text,
        );
      }
    });

    test('no existe un día imposible', () {
      expect(() => CalendarDate(2026, 2, 29), throwsArgumentError);
      expect(() => CalendarDate(2026, 0, 1), throwsArgumentError);
      expect(() => CalendarDate(2026, 4, 31), throwsArgumentError);
      expect(() => CalendarDate(0, 1, 1), throwsArgumentError);
      expect(() => CalendarDate(10000, 1, 1), throwsArgumentError);
      expect(CalendarDate(2028, 2, 29).day, 29);
    });

    test('de la hora del teléfono sale su día, sin pasar por UTC', () {
      expect(
        CalendarDate.ofLocal(DateTime(2026, 10, 31, 23, 30)),
        CalendarDate(2026, 10, 31),
      );
    });

    test('un instante en UTC no se acepta: sería el día de otra zona', () {
      expect(
        () => CalendarDate.ofLocal(DateTime.utc(2026, 11, 1, 4, 30)),
        throwsArgumentError,
      );
    });

    test('hoy es el día del reloj del teléfono', () {
      expect(
        CalendarDate.today(() => DateTime(2026, 11, 9, 0, 5)),
        CalendarDate(2026, 11, 9),
      );
    });

    test('se ordena por fecha', () {
      expect(CalendarDate(2026, 1, 31) < CalendarDate(2026, 2, 1), isTrue);
      expect(
        CalendarDate(2025, 12, 31).compareTo(CalendarDate(2026, 1, 1)),
        lessThan(0),
      );
      expect(CalendarDate(2026, 2, 1) > CalendarDate(2026, 1, 31), isTrue);
    });

    test('su mes y el primer día del mes', () {
      expect(CalendarDate(2026, 11, 9).month, 11);
      expect(CalendarDate(2026, 11, 9).yearMonth, YearMonth(2026, 11));
      expect(YearMonth(2026, 11).firstDay, CalendarDate(2026, 11, 1));
    });
  });

  group('YearMonth', () {
    test('viaja como yyyy-MM, el patrón del contrato', () {
      expect(YearMonth(2026, 3).toString(), '2026-03');
      expect(YearMonth.parse('2026-11'), YearMonth(2026, 11));
      for (final String text in <String>[
        '2026-1',
        '2026-00',
        '2026-13',
        '26-11',
        '2026-11-01',
      ]) {
        expect(
          () => YearMonth.parse(text),
          throwsFormatException,
          reason: text,
        );
      }
    });

    test('ir meses atrás cruza el año', () {
      expect(YearMonth(2026, 3).minusMonths(2), YearMonth(2026, 1));
      expect(YearMonth(2026, 3).minusMonths(3), YearMonth(2025, 12));
      expect(YearMonth(2026, 1).minusMonths(11), YearMonth(2025, 2));
      expect(YearMonth(2026, 1).minusMonths(25), YearMonth(2023, 12));
      expect(YearMonth(2026, 1).minusMonths(0), YearMonth(2026, 1));
    });

    test('el último día del mes', () {
      expect(YearMonth(2026, 2).lastDay, CalendarDate(2026, 2, 28));
      expect(YearMonth(2028, 2).lastDay, CalendarDate(2028, 2, 29));
      expect(YearMonth(2026, 12).lastDay, CalendarDate(2026, 12, 31));
    });

    test('no hay mes trece', () {
      expect(() => YearMonth(2026, 13), throwsArgumentError);
      expect(() => YearMonth(2026, 0), throwsArgumentError);
    });
  });
}

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../../tool/contract_for_dart.dart';

void main() {
  group('una fecha de calendario se genera como texto, no como DateTime', () {
    test('format: date se quita y queda un string', () {
      final Object? adjusted = calendarDatesAsText(<String, Object?>{
        'type': 'string',
        'format': 'date',
      });

      expect(adjusted, <String, Object?>{'type': 'string'});
    });

    test('un instante (date-time) sigue siendo DateTime', () {
      final Map<String, Object?> instant = <String, Object?>{
        'type': 'string',
        'format': 'date-time',
      };

      expect(calendarDatesAsText(instant), instant);
    });

    test('llega a cualquier profundidad: propiedades y parámetros', () {
      final Object? adjusted = calendarDatesAsText(<String, Object?>{
        'components': <String, Object?>{
          'schemas': <String, Object?>{
            'ExpenseRequest': <String, Object?>{
              'properties': <String, Object?>{
                'date': <String, Object?>{'type': 'string', 'format': 'date'},
                'id': <String, Object?>{'type': 'string', 'format': 'uuid'},
              },
            },
          },
        },
        'paths': <String, Object?>{
          '/api/summary': <String, Object?>{
            'get': <String, Object?>{
              'parameters': <Object?>[
                <String, Object?>{
                  'name': 'asOf',
                  'schema': <String, Object?>{
                    'type': 'string',
                    'format': 'date',
                  },
                },
              ],
            },
          },
        },
      });

      final String text = jsonEncode(adjusted);
      expect(text, isNot(contains('"date"}')));
      expect(text, isNot(contains('"format":"date"')));
      expect(text, contains('"format":"uuid"'));
      expect(text, contains('"name":"asOf"'));
    });

    test('el contrato de verdad queda sin ninguna fecha de calendario', () {
      final Object? contract = jsonDecode(
        File('../contract/openapi.json').readAsStringSync(),
      );
      final String before = jsonEncode(contract);
      final String after = jsonEncode(calendarDatesAsText(contract));

      // Si el contrato no tuviera ninguna, este test no probaría nada.
      expect(before, contains('"format":"date"'));
      expect(after, isNot(contains('"format":"date"')));
      expect(after, contains('"format":"date-time"'));
    });
  });
}

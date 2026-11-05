import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/data/api/export.dart';

import '../../support/movement_samples.dart';

/// El oneOf de MovementRequest, tal como lo generó swagger_parser.
///
/// Es el caso que peor manejan los generadores de Dart y la razón por la que
/// se eligió este: un `sealed class` con una variante por tipo, que se lee y se
/// escribe por el campo `type`. Los once tipos salen del contrato y no de una
/// lista escrita aquí: si el servidor agrega uno, este test lo recorre solo.
void main() {
  final Map<String, Object?> schemas = contractSchemas();
  final Map<String, Object?> mapping = movementTypes(schemas);

  test('el contrato tiene los once tipos de movimiento', () {
    expect(mapping, hasLength(11));
  });

  group('cada tipo de movimiento se lee y se escribe por su type', () {
    for (final MapEntry<String, Object?> entry in mapping.entries) {
      final String type = entry.key;
      final String schema = (entry.value! as String).split('/').last;

      test('$type ↔ $schema', () {
        final Map<String, Object?> json = sampleOf(schemas, schema);

        final MovementRequest read = MovementRequest.fromJson(json);

        expect(read.runtimeType.toString(), 'MovementRequest$schema');
        expect(read.toJson(), json);
      });
    }
  });

  test('un gasto se escribe con los nombres y el type del servidor', () {
    const MovementRequest expense = MovementRequestExpenseRequest(
      type: ExpenseRequestType.expense,
      id: '0a3c0f5e-2b1d-4c8e-9f00-000000000001',
      date: '2026-11-02',
      description: 'Almuerzo',
      paidFrom: '0a3c0f5e-2b1d-4c8e-9f00-000000000002',
      category: '0a3c0f5e-2b1d-4c8e-9f00-000000000003',
      amountInCents: 1234,
    );

    expect(expense.toJson(), <String, Object?>{
      'type': 'EXPENSE',
      'id': '0a3c0f5e-2b1d-4c8e-9f00-000000000001',
      // Una fecha de calendario viaja como fecha, no como instante.
      'date': '2026-11-02',
      'description': 'Almuerzo',
      'paidFrom': '0a3c0f5e-2b1d-4c8e-9f00-000000000002',
      'category': '0a3c0f5e-2b1d-4c8e-9f00-000000000003',
      'amountInCents': 1234,
    });
  });

  test('un type que no existe no se adivina: falla', () {
    expect(
      () => MovementRequest.fromJson(<String, Object?>{'type': 'GIFT'}),
      throwsFormatException,
    );
  });
}

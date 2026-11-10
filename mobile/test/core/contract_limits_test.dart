import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/money/amount_input.dart';
import 'package:mobile/core/text/text_input.dart';

import '../support/movement_samples.dart';

/// Los topes con los que la app valida el formato son los del contrato, no
/// otros escritos a mano: si el servidor cambia uno, este test lo dice antes
/// de que el teléfono rechace lo que el servidor aceptaría, o al revés.
void main() {
  final Map<String, Object?> schemas = contractSchemas();

  Map<String, Object?> property(String schema, String field) {
    final Map<String, Object?> properties =
        (schemas[schema]! as Map<String, Object?>)['properties']!
            as Map<String, Object?>;
    return properties[field]! as Map<String, Object?>;
  }

  test('el monto más grande que se puede escribir', () {
    for (final String schema in <String>[
      'ExpenseRequest',
      'IncomeRequest',
      'TransferRequest',
      'OpeningBalanceRequest',
    ]) {
      expect(
        property(schema, 'amountInCents')['maximum'],
        maxAmountCents,
        reason: schema,
      );
    }
    expect(
      property('SetBudgetRequest', 'limitInCents')['maximum'],
      maxAmountCents,
    );
  });

  test('el largo de una descripción', () {
    for (final String schema in <String>[
      'ExpenseRequest',
      'IncomeRequest',
      'TransferRequest',
    ]) {
      expect(
        property(schema, 'description')['maxLength'],
        maxDescriptionLength,
        reason: schema,
      );
    }
  });

  test('el largo de un nombre', () {
    expect(property('OpenAccountRequest', 'name')['maxLength'], maxNameLength);
    expect(property('OpenPersonRequest', 'name')['maxLength'], maxNameLength);
  });
}

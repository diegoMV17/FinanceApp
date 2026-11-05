import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/data/api/export.dart';
import 'package:mobile/data/sync/pending_movement.dart';

import '../../support/movement_samples.dart';

/// Lo que se guarda en la cola: el cuerpo exacto que se va a enviar y su id.
void main() {
  final Map<String, Object?> schemas = contractSchemas();

  group('cada tipo de movimiento entra y sale igual de la cola', () {
    for (final MapEntry<String, Object?> entry in movementTypes(
      schemas,
    ).entries) {
      test(entry.key, () {
        final Map<String, Object?> json = sampleOf(
          schemas,
          (entry.value! as String).split('/').last,
        );

        final PendingMovement pending = PendingMovement.of(
          MovementRequest.fromJson(json),
        );
        final PendingMovement restored = PendingMovement.restored(
          pending.id.value,
          pending.body,
        );

        expect(pending.id.value, sampleId);
        expect(restored.request.toJson(), json);
      });
    }
  });

  group('un guardado que no cuadra no es un pendiente', () {
    final Map<String, Object?> expense = sampleOf(schemas, 'ExpenseRequest');
    const String otherId = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';

    test('el id de la fila tiene que ser el del cuerpo', () {
      expect(
        () => PendingMovement.restored(otherId, jsonEncode(expense)),
        throwsFormatException,
      );
    });

    test('un cuerpo que no es JSON', () {
      expect(
        () => PendingMovement.restored(sampleId, 'no es json'),
        throwsFormatException,
      );
    });

    test('un JSON que no es un objeto', () {
      expect(
        () => PendingMovement.restored(sampleId, '[1, 2]'),
        throwsFormatException,
      );
    });

    test('un type que no existe', () {
      expect(
        () => PendingMovement.restored(
          sampleId,
          jsonEncode(<String, Object?>{...expense, 'type': 'GIFT'}),
        ),
        throwsFormatException,
      );
    });

    test('a un cuerpo le falta un campo', () {
      expect(
        () => PendingMovement.restored(
          sampleId,
          jsonEncode(<String, Object?>{...expense}..remove('amountInCents')),
        ),
        throwsFormatException,
      );
    });

    test('un id en el cuerpo que no es un UUID v4', () {
      expect(
        () => PendingMovement.of(
          MovementRequest.fromJson(<String, Object?>{...expense, 'id': 'x'}),
        ),
        throwsFormatException,
      );
    });
  });

  test('su texto no dice montos ni descripciones', () {
    final PendingMovement pending = PendingMovement.of(
      MovementRequest.fromJson(<String, Object?>{
        ...sampleOf(schemas, 'ExpenseRequest'),
        'description': 'Almuerzo con Juan',
        'amountInCents': 987654,
      }),
    );

    expect(pending.toString(), isNot(contains('Almuerzo')));
    expect(pending.toString(), isNot(contains('987654')));
    expect(pending.toString(), contains(sampleId));
  });
}

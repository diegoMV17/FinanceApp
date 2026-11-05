import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/data/sync/movement_id.dart';

/// El id lo genera el teléfono antes de encolar: es lo que hace seguro
/// reintentar, porque el servidor reconoce el mismo id como la misma operación.
void main() {
  final RegExp v4 = RegExp(
    r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
  );

  test('es un UUID v4', () {
    for (int i = 0; i < 200; i++) {
      expect(MovementId.generate().value, matches(v4));
    }
  });

  test('no se repite', () {
    final Set<String> ids = <String>{
      for (int i = 0; i < 1000; i++) MovementId.generate().value,
    };

    expect(ids, hasLength(1000));
  });

  test('sale del generador: uno distinto da otro id', () {
    // Un id fijo o derivado de la hora daría lo mismo con los dos.
    final String one = MovementId.generate(Random(1)).value;
    final String other = MovementId.generate(Random(2)).value;

    expect(one, isNot(other));
  });

  group('leer un id guardado', () {
    test('uno bien formado se conserva tal cual', () {
      const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';

      expect(MovementId.parse(id).value, id);
    });

    for (final String bad in <String>[
      '',
      'no-es-un-id',
      "0a3c0f5e'; DROP TABLE x; --",
      // Versión 1, no 4.
      '0a3c0f5e-2b1d-1c8e-9f00-000000000001',
      // Variante equivocada.
      '0a3c0f5e-2b1d-4c8e-1f00-000000000001',
      // Mayúsculas: otro texto para el mismo UUID, y aquí se compara el texto.
      '0A3C0F5E-2B1D-4C8E-9F00-000000000001',
      ' 0a3c0f5e-2b1d-4c8e-9f00-000000000001',
    ]) {
      test('«$bad» se rechaza', () {
        expect(() => MovementId.parse(bad), throwsFormatException);
      });
    }
  });

  test('dos con el mismo texto son el mismo id', () {
    const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';

    expect(MovementId.parse(id), MovementId.parse(id));
    expect(MovementId.parse(id).hashCode, MovementId.parse(id).hashCode);
  });
}

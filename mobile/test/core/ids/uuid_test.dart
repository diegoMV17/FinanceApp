import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/ids/uuid.dart';

/// Todo lo que el teléfono escribe lleva un id que genera él: un movimiento,
/// una persona, una cuenta, un presupuesto. Reintentar con el mismo id es lo
/// que el servidor reconoce como la misma operación.
void main() {
  test('genera un UUID v4 canónico, en minúsculas', () {
    for (int i = 0; i < 200; i++) {
      final String id = generateUuidV4();
      expect(isUuidV4(id), isTrue, reason: id);
      expect(id, id.toLowerCase());
    }
  });

  test('sale del generador: uno distinto da otro id', () {
    expect(generateUuidV4(Random(1)), isNot(generateUuidV4(Random(2))));
  });

  test('no se repite', () {
    expect(<String>{
      for (int i = 0; i < 1000; i++) generateUuidV4(),
    }, hasLength(1000));
  });

  group('reconocer un UUID', () {
    test('v4 exige la versión y la variante', () {
      expect(isUuidV4('0a3c0f5e-2b1d-4c8e-9f00-000000000001'), isTrue);
      expect(isUuidV4('0a3c0f5e-2b1d-1c8e-9f00-000000000001'), isFalse);
      expect(isUuidV4('0a3c0f5e-2b1d-4c8e-7f00-000000000001'), isFalse);
    });

    test('cualquier versión, para lo que llega del servidor', () {
      expect(isUuid('0a3c0f5e-2b1d-1c8e-9f00-000000000001'), isTrue);
      expect(isUuid('0A3C0F5E-2B1D-4C8E-9F00-000000000001'), isTrue);
    });

    test('nada que no tenga la forma', () {
      for (final String text in <String>[
        '',
        'x',
        '0a3c0f5e2b1d4c8e9f00000000000001',
        ' 0a3c0f5e-2b1d-4c8e-9f00-000000000001',
        '0a3c0f5e-2b1d-4c8e-9f00-000000000001\n',
      ]) {
        expect(isUuid(text), isFalse, reason: text);
        expect(isUuidV4(text), isFalse, reason: text);
      }
    });
  });
}

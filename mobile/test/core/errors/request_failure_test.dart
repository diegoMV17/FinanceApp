import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/errors/request_failure.dart';

import '../../support/movement_samples.dart';

/// La lista cerrada de errores del servidor, traducida a lo que lee el dueño.
/// El texto del servidor nunca se muestra: es fijo, en inglés de API, y la
/// app no le cree a un texto que viene de la red.
void main() {
  List<String> contractTypes() {
    final Map<String, Object?> problem =
        contractSchemas()['Problem']! as Map<String, Object?>;
    final Map<String, Object?> type =
        (problem['properties']! as Map<String, Object?>)['type']!
            as Map<String, Object?>;
    return (type['enum']! as List<Object?>).cast<String>();
  }

  test('cada error de la lista cerrada del contrato tiene su mensaje', () {
    final List<String> types = contractTypes();
    expect(types, isNotEmpty);
    for (final String type in types) {
      expect(
        knowsProblemType(type),
        isTrue,
        reason: '$type no tiene mensaje en core/errors',
      );
    }
  });

  test('y no sobra ninguno: un mensaje sin error es código muerto', () {
    expect(knownProblemTypes(), unorderedEquals(contractTypes()));
  });

  test('un error que la app no conoce dice algo genérico, no su texto', () {
    final String message = const RequestFailure.refused(
      'urn:finance:error:algo-nuevo',
    ).message;

    expect(message, isNotEmpty);
    expect(message, isNot(contains('algo-nuevo')));
  });

  test('los que el dueño puede arreglar dicen qué pasó', () {
    expect(
      const RequestFailure.refused('urn:finance:error:duplicate-account-name')
          .message,
      contains('nombre'),
    );
    expect(
      const RequestFailure.refused('urn:finance:error:same-account-transfer')
          .message,
      contains('misma cuenta'),
    );
  });

  test('cada clase de fallo tiene su mensaje, y son distintos', () {
    final Set<String> messages = <String>{
      for (final FailureKind kind in FailureKind.values)
        if (kind != FailureKind.refused) RequestFailure(kind).message,
    };

    expect(messages, hasLength(FailureKind.values.length - 1));
  });

  test('su toString no repite nada que venga de la red', () {
    expect(
      const RequestFailure.refused('urn:finance:error:not-found').toString(),
      'RequestFailure(refused, urn:finance:error:not-found)',
    );
  });
}

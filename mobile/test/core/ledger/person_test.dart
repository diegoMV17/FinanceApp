import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/ledger/person.dart';
import 'package:mobile/core/text/text_input.dart';

void main() {
  const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';

  test(
    'una persona por registrar: nombre obligatorio, sin espacios de más',
    () {
      expect(PersonDraft(id: id, name: '  Juan ').name, 'Juan');
      expect(
        () => PersonDraft(id: id, name: '  '),
        throwsA(isA<TextInputException>()),
      );
    },
  );

  test('con el id v4 del teléfono, y sin imprimir el nombre', () {
    expect(() => PersonDraft(id: 'x', name: 'Juan'), throwsArgumentError);
    expect(
      PersonDraft(id: id, name: 'Juan').toString(),
      isNot(contains('Juan')),
    );
  });
}

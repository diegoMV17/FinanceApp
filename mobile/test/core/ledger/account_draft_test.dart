import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/ledger/account_draft.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/text/text_input.dart';

void main() {
  const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';

  test('guarda el nombre sin los espacios de los lados', () {
    expect(
      AccountDraft(id: id, name: '  Nequi ', kind: AccountKind.asset).name,
      'Nequi',
    );
  });

  test('el nombre es obligatorio y cabe', () {
    expect(
      () => AccountDraft(id: id, name: ' ', kind: AccountKind.asset),
      throwsA(isA<TextInputException>()),
    );
    expect(
      () => AccountDraft(
        id: id,
        name: 'a' * (maxNameLength + 1),
        kind: AccountKind.asset,
      ),
      throwsA(isA<TextInputException>()),
    );
  });

  test('el id es un UUID v4 del teléfono', () {
    expect(
      () => AccountDraft(id: 'x', name: 'Nequi', kind: AccountKind.asset),
      throwsArgumentError,
    );
  });

  test('no imprime el nombre', () {
    expect(
      AccountDraft(id: id, name: 'Nequi', kind: AccountKind.asset).toString(),
      isNot(contains('Nequi')),
    );
  });
}

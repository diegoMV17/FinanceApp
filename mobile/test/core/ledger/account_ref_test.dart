import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/ledger/account_ref.dart';

import '../../support/movement_samples.dart';

void main() {
  const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';

  test('una cuenta como la ven las pantallas', () {
    const AccountRef nequi = AccountRef(
      id: id,
      name: 'Nequi',
      kind: AccountKind.asset,
    );

    expect(nequi.isArchived, isFalse);
    expect(nequi.id, id);
  });

  test('cada tipo del contrato tiene el suyo, y ninguno sobra', () {
    final Map<String, Object?> schemas = contractSchemas();
    final List<Object?> wire =
        (schemas['AccountType']! as Map<String, Object?>)['enum']!
            as List<Object?>;

    expect(
      AccountKind.values.map((AccountKind k) => k.wire),
      unorderedEquals(wire),
    );
    for (final AccountKind kind in AccountKind.values) {
      expect(AccountKind.fromWire(kind.wire), kind);
    }
  });

  test('un tipo que no conoce es un error, no un tipo por omisión', () {
    expect(() => AccountKind.fromWire('SAVINGS'), throwsFormatException);
  });

  test('las que pueden ser de una persona son las tres que son alguien', () {
    expect(
      AccountKind.values.where((AccountKind k) => k.belongsToAPerson),
      unorderedEquals(<AccountKind>[
        AccountKind.receivable,
        AccountKind.payable,
        AccountKind.thirdPartyFund,
      ]),
    );
  });

  test('cada tipo tiene su nombre en español', () {
    expect(
      AccountKind.values.map((AccountKind k) => k.label).toSet(),
      hasLength(AccountKind.values.length),
    );
    expect(AccountKind.expense.label, 'Categoría de gasto');
  });
}

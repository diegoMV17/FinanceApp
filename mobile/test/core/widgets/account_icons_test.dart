import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/widgets/account_icons.dart';

void main() {
  test('cada tipo de cuenta tiene su icono, y no se repiten', () {
    expect(
      AccountKind.values.map(iconForKind).toSet(),
      hasLength(AccountKind.values.length),
    );
    expect(
      iconForKind(AccountKind.asset),
      Icons.account_balance_wallet_outlined,
    );
  });
}

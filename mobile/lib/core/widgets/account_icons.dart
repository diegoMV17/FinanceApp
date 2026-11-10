import 'package:flutter/material.dart';

import '../ledger/account_ref.dart';

/// El icono de cada tipo de cuenta, el mismo en todas las pantallas.
IconData iconForKind(AccountKind kind) => switch (kind) {
  AccountKind.asset => Icons.account_balance_wallet_outlined,
  AccountKind.receivable => Icons.call_received,
  AccountKind.payable => Icons.call_made,
  AccountKind.thirdPartyFund => Icons.volunteer_activism_outlined,
  AccountKind.income => Icons.trending_up,
  AccountKind.expense => Icons.shopping_bag_outlined,
  AccountKind.equity => Icons.account_balance_outlined,
};

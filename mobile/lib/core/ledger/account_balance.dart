import '../money/money.dart';
import 'account_ref.dart';

/// Una cuenta con el saldo que dijo el servidor. Lo comparten el tablero y
/// las personas; la app no lo calcula ni lo corrige.
class AccountBalance {
  const AccountBalance(this.account, this.balance);

  final AccountRef account;
  final Money balance;

  @override
  bool operator ==(Object other) =>
      other is AccountBalance &&
      other.account == account &&
      other.balance == balance;

  @override
  int get hashCode => Object.hash(account, balance);
}

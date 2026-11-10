import '../ids/uuid.dart';
import '../money/money.dart';
import '../text/text_input.dart';
import 'account_balance.dart';

/// Lo que alguien me debe, lo que le debo y la plata suya que guardo, como lo
/// dice `GET /api/people`. La app no lo deriva de las cuentas: lo hace el
/// servidor, con el signo que lee la persona ya aplicado.
class PersonPosition {
  const PersonPosition({
    required this.id,
    required this.name,
    required this.isArchived,
    required this.owedToYou,
    required this.youOwe,
    required this.theirFund,
    required this.netOwedToYou,
    required this.accounts,
  });

  final String id;
  final String name;
  final bool isArchived;
  final Money owedToYou;
  final Money youOwe;
  final Money theirFund;
  final Money netOwedToYou;

  /// Las cuentas que explican esas cifras.
  final List<AccountBalance> accounts;
}

/// Una persona por registrar, con el id que generó el teléfono una vez por
/// formulario: reintentar la misma no crea otra.
class PersonDraft {
  PersonDraft({required this.id, required String name})
    : name = checkedName(name) {
    if (!isUuidV4(id)) {
      throw ArgumentError.value('(oculto)', 'id', 'no es un UUID v4');
    }
  }

  final String id;
  final String name;

  @override
  String toString() => 'PersonDraft($id)';
}

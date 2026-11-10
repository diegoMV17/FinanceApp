import '../ids/uuid.dart';
import '../text/text_input.dart';
import 'account_ref.dart';

/// Una cuenta por abrir, con el id que generó el teléfono.
///
/// El id se genera una vez por formulario: si la respuesta no llega y se
/// reintenta, el servidor reconoce la misma cuenta y responde 200 en vez de
/// abrir otra. La comparten las cuentas y las personas (abrir la cuenta de
/// alguien).
class AccountDraft {
  AccountDraft({required this.id, required String name, required this.kind})
    : name = checkedName(name) {
    if (!isUuidV4(id)) {
      throw ArgumentError.value('(oculto)', 'id', 'no es un UUID v4');
    }
  }

  final String id;
  final String name;
  final AccountKind kind;

  @override
  String toString() => 'AccountDraft($id, ${kind.wire})';
}

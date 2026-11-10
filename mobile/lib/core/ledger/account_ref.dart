/// Una cuenta tal como la ven las pantallas: para elegirla en un formulario o
/// para nombrarla en un tablero. No lleva saldo: el saldo es una lectura con
/// su fecha, y vive en quien la hizo.
///
/// Está en `core/` porque la usan varias funcionalidades (registrar, personas,
/// presupuestos) y una no puede importar el interior de otra.
class AccountRef {
  const AccountRef({
    required this.id,
    required this.name,
    required this.kind,
    this.isArchived = false,
  });

  final String id;
  final String name;
  final AccountKind kind;
  final bool isArchived;

  @override
  bool operator ==(Object other) =>
      other is AccountRef &&
      other.id == id &&
      other.name == name &&
      other.kind == kind &&
      other.isArchived == isArchived;

  @override
  int get hashCode => Object.hash(id, name, kind, isArchived);
}

/// Los siete tipos de cuenta del servidor (`AccountType` en el contrato).
enum AccountKind {
  asset('ASSET', 'Cuenta de plata'),
  receivable('RECEIVABLE', 'Me deben'),
  payable('PAYABLE', 'Debo'),
  thirdPartyFund('THIRD_PARTY_FUND', 'Plata de otro'),
  income('INCOME', 'Categoría de ingreso'),
  expense('EXPENSE', 'Categoría de gasto'),
  equity('EQUITY', 'Patrimonio');

  const AccountKind(this.wire, this.label);

  /// Como viaja en el contrato.
  final String wire;

  /// Como se lee en pantalla.
  final String label;

  /// Lanza [FormatException] con un tipo que no conoce: inventar uno por
  /// omisión pondría una cuenta en el lado equivocado de la pantalla.
  static AccountKind fromWire(String wire) {
    for (final AccountKind kind in values) {
      if (kind.wire == wire) {
        return kind;
      }
    }
    throw const FormatException('tipo de cuenta desconocido');
  }

  /// Los tres tipos que son alguien: lo que me deben, lo que debo y la plata
  /// que guardo de otro. Solo estos se vinculan a una persona (lo decide el
  /// servidor; esto solo filtra qué ofrecer).
  bool get belongsToAPerson =>
      this == receivable || this == payable || this == thirdPartyFund;
}

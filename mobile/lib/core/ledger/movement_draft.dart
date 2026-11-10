import '../dates/calendar_date.dart';
import '../ids/uuid.dart';
import '../money/amount_input.dart';
import '../money/money.dart';
import '../text/text_input.dart';

/// Un movimiento que el usuario registra, antes de encolarse.
///
/// Es el modelo de la app, no el del contrato: las pantallas no ven el cliente
/// generado. `data/sync` lo traduce a `MovementRequest`. Es imposible
/// construir uno inválido de formato: el id es un UUID v4 generado en el
/// teléfono, el monto es positivo y dentro del tope, cada cuenta es un UUID y
/// la descripción cabe. Que las cuentas sean del tipo correcto, distintas o
/// abiertas lo decide el servidor.
///
/// El monto es siempre positivo: de qué lado del asiento va lo decide el
/// servidor según el tipo de movimiento.
sealed class MovementDraft {
  MovementDraft({required this.id, required this.date, required this.amount}) {
    if (!isUuidV4(id)) {
      throw ArgumentError.value('(oculto)', 'id', 'no es un UUID v4');
    }
    if (!amount.isPositive || amount.cents > maxAmountCents) {
      throw ArgumentError.value('(oculto)', 'amount', 'fuera de 1..tope');
    }
  }

  final String id;
  final CalendarDate date;
  final Money amount;

  // Sin el monto ni la descripción: un movimiento no se imprime en un log.
  @override
  String toString() => '$runtimeType($id)';
}

/// Plata que sale de una cuenta hacia una categoría de gasto.
final class ExpenseDraft extends MovementDraft {
  ExpenseDraft({
    required super.id,
    required super.date,
    required super.amount,
    required String description,
    required this.paidFrom,
    required this.category,
  }) : description = checkedDescription(description) {
    _requireAccount('paidFrom', paidFrom);
    _requireAccount('category', category);
  }

  final String description;
  final String paidFrom;
  final String category;
}

/// Plata que entra a una cuenta desde una categoría de ingreso.
final class IncomeDraft extends MovementDraft {
  IncomeDraft({
    required super.id,
    required super.date,
    required super.amount,
    required String description,
    required this.receivedInto,
    required this.category,
  }) : description = checkedDescription(description) {
    _requireAccount('receivedInto', receivedInto);
    _requireAccount('category', category);
  }

  final String description;
  final String receivedInto;
  final String category;
}

/// Plata que pasa de una cuenta propia a otra.
final class TransferDraft extends MovementDraft {
  TransferDraft({
    required super.id,
    required super.date,
    required super.amount,
    required String description,
    required this.from,
    required this.to,
  }) : description = checkedDescription(description) {
    _requireAccount('from', from);
    _requireAccount('to', to);
  }

  final String description;
  final String from;
  final String to;
}

/// Lo que una cuenta ya tenía el día que empezó a llevarse aquí, contra el
/// patrimonio. No lleva descripción: el contrato no la tiene.
final class OpeningBalanceDraft extends MovementDraft {
  OpeningBalanceDraft({
    required super.id,
    required super.date,
    required super.amount,
    required this.account,
    required this.equity,
  }) {
    _requireAccount('account', account);
    _requireAccount('equity', equity);
  }

  final String account;
  final String equity;
}

void _requireAccount(String name, String id) {
  if (!isUuid(id)) {
    throw ArgumentError.value('(oculto)', name, 'no es el id de una cuenta');
  }
}

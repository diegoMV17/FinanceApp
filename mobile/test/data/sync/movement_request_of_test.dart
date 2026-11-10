import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/ledger/movement_draft.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/data/api/export.dart';
import 'package:mobile/data/sync/movement_request_of.dart';
import 'package:mobile/data/sync/pending_movement.dart';

import '../../support/movement_samples.dart';

/// La traducción de lo que escribe el usuario al cuerpo que viaja. Es el
/// espejo de MovementRequestCoverageTest del servidor: **cada cuenta llega a
/// su hueco por nombre**. Un `paidFrom` que acabe en `category` arma un
/// asiento balanceado, pasa todas las reglas del libro y guarda el gasto al
/// revés; por eso cada hueco lleva un id distinto y se comprueba uno por uno.
void main() {
  const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String a = 'aaaaaaaa-2b1d-4c8e-9f00-000000000001';
  const String b = 'bbbbbbbb-2b1d-4c8e-9f00-000000000002';
  final CalendarDate date = CalendarDate(2026, 10, 31);
  final Money amount = Money.ofCents(1234567);
  final Map<String, Object?> schemas = contractSchemas();

  Map<String, Object?> wire(MovementDraft draft) =>
      movementRequestOf(draft).toJson();

  /// Los campos que pide el contrato para esa variante, ni uno más ni uno
  /// menos.
  void coversTheContract(Map<String, Object?> json, String schema) {
    final List<Object?> required =
        (schemas[schema]! as Map<String, Object?>)['required']!
            as List<Object?>;
    expect(json.keys, unorderedEquals(required), reason: schema);
  }

  test('un gasto: sale de paidFrom y va a category', () {
    final Map<String, Object?> json = wire(
      ExpenseDraft(
        id: id,
        date: date,
        amount: amount,
        description: 'Almuerzo',
        paidFrom: a,
        category: b,
      ),
    );

    expect(json['type'], 'EXPENSE');
    expect(json['paidFrom'], a);
    expect(json['category'], b);
    expect(json['amountInCents'], 1234567);
    expect(json['date'], '2026-10-31');
    expect(json['description'], 'Almuerzo');
    expect(json['id'], id);
    coversTheContract(json, 'ExpenseRequest');
  });

  test('un ingreso: entra a receivedInto desde category', () {
    final Map<String, Object?> json = wire(
      IncomeDraft(
        id: id,
        date: date,
        amount: amount,
        description: 'Sueldo',
        receivedInto: a,
        category: b,
      ),
    );

    expect(json['type'], 'INCOME');
    expect(json['receivedInto'], a);
    expect(json['category'], b);
    expect(json['amountInCents'], 1234567);
    expect(json['description'], 'Sueldo');
    coversTheContract(json, 'IncomeRequest');
  });

  test('una transferencia: de from a to, sin invertirlas', () {
    final Map<String, Object?> json = wire(
      TransferDraft(
        id: id,
        date: date,
        amount: amount,
        description: 'Ahorro',
        from: a,
        to: b,
      ),
    );

    expect(json['type'], 'TRANSFER');
    expect(json['from'], a);
    expect(json['to'], b);
    expect(json['amountInCents'], 1234567);
    coversTheContract(json, 'TransferRequest');
  });

  test('un saldo inicial: la cuenta y el patrimonio, cada uno en su hueco', () {
    final Map<String, Object?> json = wire(
      OpeningBalanceDraft(
        id: id,
        date: date,
        amount: amount,
        account: a,
        equity: b,
      ),
    );

    expect(json['type'], 'OPENING_BALANCE');
    expect(json['account'], a);
    expect(json['equity'], b);
    expect(json['amountInCents'], 1234567);
    expect(json['date'], '2026-10-31');
    coversTheContract(json, 'OpeningBalanceRequest');
  });

  test('lo traducido se puede encolar: el id es el del cuerpo', () {
    final MovementRequest request = movementRequestOf(
      ExpenseDraft(
        id: id,
        date: date,
        amount: amount,
        description: '',
        paidFrom: a,
        category: b,
      ),
    );

    expect(PendingMovement.of(request).id.value, id);
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/dates/calendar_date.dart';
import 'package:mobile/core/ledger/movement_draft.dart';
import 'package:mobile/core/money/amount_input.dart';
import 'package:mobile/core/money/money.dart';
import 'package:mobile/core/text/text_input.dart';

/// Lo que el usuario registra, armado y válido antes de salir de la pantalla:
/// es imposible construir uno con un monto en cero, un id que no es UUID v4 o
/// una descripción más larga que el tope.
void main() {
  const String id = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String nequi = '1b3c0f5e-2b1d-4c8e-9f00-000000000002';
  const String food = '2c3c0f5e-2b1d-4c8e-9f00-000000000003';
  final CalendarDate date = CalendarDate(2026, 11, 9);

  ExpenseDraft expense({
    String id = id,
    Money? amount,
    String description = 'Almuerzo',
    String paidFrom = nequi,
  }) => ExpenseDraft(
    id: id,
    date: date,
    amount: amount ?? Money.ofCents(1250000),
    description: description,
    paidFrom: paidFrom,
    category: food,
  );

  test('uno válido guarda lo que se escribió', () {
    final ExpenseDraft draft = expense(description: '  Almuerzo  ');

    expect(draft.amount, Money.ofCents(1250000));
    expect(draft.description, 'Almuerzo');
    expect(draft.paidFrom, nequi);
  });

  test('el id lo genera el teléfono: un UUID v4', () {
    expect(() => expense(id: 'x'), throwsArgumentError);
    expect(
      () => expense(id: '0a3c0f5e-2b1d-1c8e-9f00-000000000001'),
      throwsArgumentError,
    );
  });

  test('el monto es positivo y dentro del tope', () {
    expect(() => expense(amount: Money.zero), throwsArgumentError);
    expect(() => expense(amount: Money.ofCents(-100)), throwsArgumentError);
    expect(
      () => expense(amount: Money.ofCents(maxAmountCents + 1)),
      throwsArgumentError,
    );
    expect(
      expense(amount: Money.ofCents(maxAmountCents)).amount.cents,
      maxAmountCents,
    );
  });

  test('la descripción dentro de su tope', () {
    expect(
      () => expense(description: 'a' * (maxDescriptionLength + 1)),
      throwsA(isA<TextInputException>()),
    );
  });

  test('cada cuenta es un UUID', () {
    expect(() => expense(paidFrom: ''), throwsArgumentError);
    expect(
      () => TransferDraft(
        id: id,
        date: date,
        amount: Money.ofCents(100),
        description: '',
        from: nequi,
        to: 'otra',
      ),
      throwsArgumentError,
    );
  });

  test('no repite el monto ni la descripción al imprimirse', () {
    final String printed = expense(description: 'Almuerzo con Juan').toString();

    expect(printed, isNot(contains('Almuerzo')));
    expect(printed, isNot(contains('12500')));
  });
}

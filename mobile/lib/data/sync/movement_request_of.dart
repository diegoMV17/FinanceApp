import '../../core/ledger/movement_draft.dart';
import '../api/export.dart';

/// Lo que escribió el usuario, como el cuerpo de `POST /api/movements`.
///
/// Cada cuenta va a su hueco **por nombre**: un `paidFrom` que terminara en
/// `category` sería un asiento balanceado que el servidor acepta, y el gasto
/// quedaría al revés sin que nada se queje. El test lo comprueba hueco por
/// hueco, con ids distintos.
MovementRequest movementRequestOf(MovementDraft draft) => switch (draft) {
  ExpenseDraft() => MovementRequestExpenseRequest(
    type: ExpenseRequestType.expense,
    id: draft.id,
    date: draft.date.toString(),
    amountInCents: draft.amount.cents,
    description: draft.description,
    paidFrom: draft.paidFrom,
    category: draft.category,
  ),
  IncomeDraft() => MovementRequestIncomeRequest(
    type: IncomeRequestType.income,
    id: draft.id,
    date: draft.date.toString(),
    amountInCents: draft.amount.cents,
    description: draft.description,
    receivedInto: draft.receivedInto,
    category: draft.category,
  ),
  TransferDraft() => MovementRequestTransferRequest(
    type: TransferRequestType.transfer,
    id: draft.id,
    date: draft.date.toString(),
    amountInCents: draft.amount.cents,
    description: draft.description,
    from: draft.from,
    to: draft.to,
  ),
  OpeningBalanceDraft() => MovementRequestOpeningBalanceRequest(
    type: OpeningBalanceRequestType.openingBalance,
    id: draft.id,
    date: draft.date.toString(),
    amountInCents: draft.amount.cents,
    account: draft.account,
    equity: draft.equity,
  ),
};

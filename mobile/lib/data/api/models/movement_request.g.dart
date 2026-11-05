// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'movement_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Map<String, dynamic> _$MovementRequestToJson(MovementRequest instance) =>
    <String, dynamic>{};

MovementRequestBorrowRequest _$MovementRequestBorrowRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestBorrowRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  id: json['id'] as String,
  lender: json['lender'] as String,
  receivedInto: json['receivedInto'] as String,
  type: BorrowRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestBorrowRequestToJson(
  MovementRequestBorrowRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'date': instance.date,
  'description': instance.description,
  'id': instance.id,
  'lender': instance.lender,
  'receivedInto': instance.receivedInto,
  'type': _$BorrowRequestTypeEnumMap[instance.type]!,
};

const _$BorrowRequestTypeEnumMap = {
  BorrowRequestType.borrow: 'BORROW',
  BorrowRequestType.$unknown: r'$unknown',
};

MovementRequestCollectLoanRequest _$MovementRequestCollectLoanRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestCollectLoanRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  borrower: json['borrower'] as String,
  date: json['date'] as String,
  description: json['description'] as String,
  id: json['id'] as String,
  receivedInto: json['receivedInto'] as String,
  type: CollectLoanRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestCollectLoanRequestToJson(
  MovementRequestCollectLoanRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'borrower': instance.borrower,
  'date': instance.date,
  'description': instance.description,
  'id': instance.id,
  'receivedInto': instance.receivedInto,
  'type': _$CollectLoanRequestTypeEnumMap[instance.type]!,
};

const _$CollectLoanRequestTypeEnumMap = {
  CollectLoanRequestType.collectLoan: 'COLLECT_LOAN',
  CollectLoanRequestType.$unknown: r'$unknown',
};

MovementRequestExpenseRequest _$MovementRequestExpenseRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestExpenseRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  category: json['category'] as String,
  date: json['date'] as String,
  description: json['description'] as String,
  id: json['id'] as String,
  paidFrom: json['paidFrom'] as String,
  type: ExpenseRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestExpenseRequestToJson(
  MovementRequestExpenseRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'category': instance.category,
  'date': instance.date,
  'description': instance.description,
  'id': instance.id,
  'paidFrom': instance.paidFrom,
  'type': _$ExpenseRequestTypeEnumMap[instance.type]!,
};

const _$ExpenseRequestTypeEnumMap = {
  ExpenseRequestType.expense: 'EXPENSE',
  ExpenseRequestType.$unknown: r'$unknown',
};

MovementRequestIncomeRequest _$MovementRequestIncomeRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestIncomeRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  category: json['category'] as String,
  date: json['date'] as String,
  description: json['description'] as String,
  id: json['id'] as String,
  receivedInto: json['receivedInto'] as String,
  type: IncomeRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestIncomeRequestToJson(
  MovementRequestIncomeRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'category': instance.category,
  'date': instance.date,
  'description': instance.description,
  'id': instance.id,
  'receivedInto': instance.receivedInto,
  'type': _$IncomeRequestTypeEnumMap[instance.type]!,
};

const _$IncomeRequestTypeEnumMap = {
  IncomeRequestType.income: 'INCOME',
  IncomeRequestType.$unknown: r'$unknown',
};

MovementRequestLendRequest _$MovementRequestLendRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestLendRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  borrower: json['borrower'] as String,
  date: json['date'] as String,
  description: json['description'] as String,
  id: json['id'] as String,
  paidFrom: json['paidFrom'] as String,
  type: LendRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestLendRequestToJson(
  MovementRequestLendRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'borrower': instance.borrower,
  'date': instance.date,
  'description': instance.description,
  'id': instance.id,
  'paidFrom': instance.paidFrom,
  'type': _$LendRequestTypeEnumMap[instance.type]!,
};

const _$LendRequestTypeEnumMap = {
  LendRequestType.lend: 'LEND',
  LendRequestType.$unknown: r'$unknown',
};

MovementRequestOpeningBalanceRequest
_$MovementRequestOpeningBalanceRequestFromJson(Map<String, dynamic> json) =>
    MovementRequestOpeningBalanceRequest(
      account: json['account'] as String,
      amountInCents: (json['amountInCents'] as num).toInt(),
      date: json['date'] as String,
      equity: json['equity'] as String,
      id: json['id'] as String,
      type: OpeningBalanceRequestType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$MovementRequestOpeningBalanceRequestToJson(
  MovementRequestOpeningBalanceRequest instance,
) => <String, dynamic>{
  'account': instance.account,
  'amountInCents': instance.amountInCents,
  'date': instance.date,
  'equity': instance.equity,
  'id': instance.id,
  'type': _$OpeningBalanceRequestTypeEnumMap[instance.type]!,
};

const _$OpeningBalanceRequestTypeEnumMap = {
  OpeningBalanceRequestType.openingBalance: 'OPENING_BALANCE',
  OpeningBalanceRequestType.$unknown: r'$unknown',
};

MovementRequestReceiveThirdPartyMoneyRequest
_$MovementRequestReceiveThirdPartyMoneyRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestReceiveThirdPartyMoneyRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  fund: json['fund'] as String,
  heldIn: json['heldIn'] as String,
  id: json['id'] as String,
  type: ReceiveThirdPartyMoneyRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestReceiveThirdPartyMoneyRequestToJson(
  MovementRequestReceiveThirdPartyMoneyRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'date': instance.date,
  'description': instance.description,
  'fund': instance.fund,
  'heldIn': instance.heldIn,
  'id': instance.id,
  'type': _$ReceiveThirdPartyMoneyRequestTypeEnumMap[instance.type]!,
};

const _$ReceiveThirdPartyMoneyRequestTypeEnumMap = {
  ReceiveThirdPartyMoneyRequestType.receiveThirdPartyMoney:
      'RECEIVE_THIRD_PARTY_MONEY',
  ReceiveThirdPartyMoneyRequestType.$unknown: r'$unknown',
};

MovementRequestRepayDebtRequest _$MovementRequestRepayDebtRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestRepayDebtRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  id: json['id'] as String,
  lender: json['lender'] as String,
  paidFrom: json['paidFrom'] as String,
  type: RepayDebtRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestRepayDebtRequestToJson(
  MovementRequestRepayDebtRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'date': instance.date,
  'description': instance.description,
  'id': instance.id,
  'lender': instance.lender,
  'paidFrom': instance.paidFrom,
  'type': _$RepayDebtRequestTypeEnumMap[instance.type]!,
};

const _$RepayDebtRequestTypeEnumMap = {
  RepayDebtRequestType.repayDebt: 'REPAY_DEBT',
  RepayDebtRequestType.$unknown: r'$unknown',
};

MovementRequestReturnThirdPartyMoneyRequest
_$MovementRequestReturnThirdPartyMoneyRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestReturnThirdPartyMoneyRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  fund: json['fund'] as String,
  id: json['id'] as String,
  paidFrom: json['paidFrom'] as String,
  type: ReturnThirdPartyMoneyRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestReturnThirdPartyMoneyRequestToJson(
  MovementRequestReturnThirdPartyMoneyRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'date': instance.date,
  'description': instance.description,
  'fund': instance.fund,
  'id': instance.id,
  'paidFrom': instance.paidFrom,
  'type': _$ReturnThirdPartyMoneyRequestTypeEnumMap[instance.type]!,
};

const _$ReturnThirdPartyMoneyRequestTypeEnumMap = {
  ReturnThirdPartyMoneyRequestType.returnThirdPartyMoney:
      'RETURN_THIRD_PARTY_MONEY',
  ReturnThirdPartyMoneyRequestType.$unknown: r'$unknown',
};

MovementRequestSpendThirdPartyMoneyRequest
_$MovementRequestSpendThirdPartyMoneyRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestSpendThirdPartyMoneyRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  fund: json['fund'] as String,
  id: json['id'] as String,
  paidFrom: json['paidFrom'] as String,
  type: SpendThirdPartyMoneyRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestSpendThirdPartyMoneyRequestToJson(
  MovementRequestSpendThirdPartyMoneyRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'date': instance.date,
  'description': instance.description,
  'fund': instance.fund,
  'id': instance.id,
  'paidFrom': instance.paidFrom,
  'type': _$SpendThirdPartyMoneyRequestTypeEnumMap[instance.type]!,
};

const _$SpendThirdPartyMoneyRequestTypeEnumMap = {
  SpendThirdPartyMoneyRequestType.spendThirdPartyMoney:
      'SPEND_THIRD_PARTY_MONEY',
  SpendThirdPartyMoneyRequestType.$unknown: r'$unknown',
};

MovementRequestTransferRequest _$MovementRequestTransferRequestFromJson(
  Map<String, dynamic> json,
) => MovementRequestTransferRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  from: json['from'] as String,
  id: json['id'] as String,
  to: json['to'] as String,
  type: TransferRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$MovementRequestTransferRequestToJson(
  MovementRequestTransferRequest instance,
) => <String, dynamic>{
  'amountInCents': instance.amountInCents,
  'date': instance.date,
  'description': instance.description,
  'from': instance.from,
  'id': instance.id,
  'to': instance.to,
  'type': _$TransferRequestTypeEnumMap[instance.type]!,
};

const _$TransferRequestTypeEnumMap = {
  TransferRequestType.transfer: 'TRANSFER',
  TransferRequestType.$unknown: r'$unknown',
};

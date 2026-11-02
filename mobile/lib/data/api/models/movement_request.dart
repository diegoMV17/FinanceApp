// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'borrow_request.dart';
import 'borrow_request_type.dart';
import 'collect_loan_request.dart';
import 'collect_loan_request_type.dart';
import 'expense_request.dart';
import 'expense_request_type.dart';
import 'income_request.dart';
import 'income_request_type.dart';
import 'lend_request.dart';
import 'lend_request_type.dart';
import 'opening_balance_request.dart';
import 'opening_balance_request_type.dart';
import 'receive_third_party_money_request.dart';
import 'receive_third_party_money_request_type.dart';
import 'repay_debt_request.dart';
import 'repay_debt_request_type.dart';
import 'return_third_party_money_request.dart';
import 'return_third_party_money_request_type.dart';
import 'spend_third_party_money_request.dart';
import 'spend_third_party_money_request_type.dart';
import 'transfer_request.dart';
import 'transfer_request_type.dart';

part 'movement_request.g.dart';

/// Un movimiento, uno de once tipos según el campo type.
@JsonSerializable(createFactory: false)
sealed class MovementRequest {
  const MovementRequest();

  factory MovementRequest.fromJson(Map<String, dynamic> json) =>
      MovementRequestSealedDeserializer.tryDeserialize(json);

  Map<String, dynamic> toJson();
}

extension MovementRequestSealedDeserializer on MovementRequest {
  static MovementRequest tryDeserialize(
    Map<String, dynamic> json, {
    String key = 'type',
    Map<Type, Object?>? mapping,
  }) {
    final mappingFallback = const <Type, Object?>{
      MovementRequestBorrowRequest: 'BORROW',
      MovementRequestCollectLoanRequest: 'COLLECT_LOAN',
      MovementRequestExpenseRequest: 'EXPENSE',
      MovementRequestIncomeRequest: 'INCOME',
      MovementRequestLendRequest: 'LEND',
      MovementRequestOpeningBalanceRequest: 'OPENING_BALANCE',
      MovementRequestReceiveThirdPartyMoneyRequest: 'RECEIVE_THIRD_PARTY_MONEY',
      MovementRequestRepayDebtRequest: 'REPAY_DEBT',
      MovementRequestReturnThirdPartyMoneyRequest: 'RETURN_THIRD_PARTY_MONEY',
      MovementRequestSpendThirdPartyMoneyRequest: 'SPEND_THIRD_PARTY_MONEY',
      MovementRequestTransferRequest: 'TRANSFER',
    };
    final value = json[key];
    final effective = mapping ?? mappingFallback;
    return switch (value) {
      _ when value == effective[MovementRequestBorrowRequest] =>
        MovementRequestBorrowRequest.fromJson(json),
      _ when value == effective[MovementRequestCollectLoanRequest] =>
        MovementRequestCollectLoanRequest.fromJson(json),
      _ when value == effective[MovementRequestExpenseRequest] =>
        MovementRequestExpenseRequest.fromJson(json),
      _ when value == effective[MovementRequestIncomeRequest] =>
        MovementRequestIncomeRequest.fromJson(json),
      _ when value == effective[MovementRequestLendRequest] =>
        MovementRequestLendRequest.fromJson(json),
      _ when value == effective[MovementRequestOpeningBalanceRequest] =>
        MovementRequestOpeningBalanceRequest.fromJson(json),
      _ when value == effective[MovementRequestReceiveThirdPartyMoneyRequest] =>
        MovementRequestReceiveThirdPartyMoneyRequest.fromJson(json),
      _ when value == effective[MovementRequestRepayDebtRequest] =>
        MovementRequestRepayDebtRequest.fromJson(json),
      _ when value == effective[MovementRequestReturnThirdPartyMoneyRequest] =>
        MovementRequestReturnThirdPartyMoneyRequest.fromJson(json),
      _ when value == effective[MovementRequestSpendThirdPartyMoneyRequest] =>
        MovementRequestSpendThirdPartyMoneyRequest.fromJson(json),
      _ when value == effective[MovementRequestTransferRequest] =>
        MovementRequestTransferRequest.fromJson(json),
      _ => throw FormatException(
        'Unknown discriminator value "${json[key]}" for MovementRequest',
      ),
    };
  }
}

@JsonSerializable()
class MovementRequestBorrowRequest extends MovementRequest
    implements BorrowRequest {
  @override
  final int amountInCents;
  @override
  final String date;
  @override
  final String description;
  @override
  final String id;
  @override
  final String lender;
  @override
  final String receivedInto;
  @override
  final BorrowRequestType type;

  const MovementRequestBorrowRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.id,
    required this.lender,
    required this.receivedInto,
    required this.type,
  });

  factory MovementRequestBorrowRequest.fromJson(Map<String, dynamic> json) =>
      _$MovementRequestBorrowRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() => _$MovementRequestBorrowRequestToJson(this);
}

@JsonSerializable()
class MovementRequestCollectLoanRequest extends MovementRequest
    implements CollectLoanRequest {
  @override
  final int amountInCents;
  @override
  final String borrower;
  @override
  final String date;
  @override
  final String description;
  @override
  final String id;
  @override
  final String receivedInto;
  @override
  final CollectLoanRequestType type;

  const MovementRequestCollectLoanRequest({
    required this.amountInCents,
    required this.borrower,
    required this.date,
    required this.description,
    required this.id,
    required this.receivedInto,
    required this.type,
  });

  factory MovementRequestCollectLoanRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$MovementRequestCollectLoanRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() =>
      _$MovementRequestCollectLoanRequestToJson(this);
}

@JsonSerializable()
class MovementRequestExpenseRequest extends MovementRequest
    implements ExpenseRequest {
  @override
  final int amountInCents;
  @override
  final String category;
  @override
  final String date;
  @override
  final String description;
  @override
  final String id;
  @override
  final String paidFrom;
  @override
  final ExpenseRequestType type;

  const MovementRequestExpenseRequest({
    required this.amountInCents,
    required this.category,
    required this.date,
    required this.description,
    required this.id,
    required this.paidFrom,
    required this.type,
  });

  factory MovementRequestExpenseRequest.fromJson(Map<String, dynamic> json) =>
      _$MovementRequestExpenseRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() => _$MovementRequestExpenseRequestToJson(this);
}

@JsonSerializable()
class MovementRequestIncomeRequest extends MovementRequest
    implements IncomeRequest {
  @override
  final int amountInCents;
  @override
  final String category;
  @override
  final String date;
  @override
  final String description;
  @override
  final String id;
  @override
  final String receivedInto;
  @override
  final IncomeRequestType type;

  const MovementRequestIncomeRequest({
    required this.amountInCents,
    required this.category,
    required this.date,
    required this.description,
    required this.id,
    required this.receivedInto,
    required this.type,
  });

  factory MovementRequestIncomeRequest.fromJson(Map<String, dynamic> json) =>
      _$MovementRequestIncomeRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() => _$MovementRequestIncomeRequestToJson(this);
}

@JsonSerializable()
class MovementRequestLendRequest extends MovementRequest
    implements LendRequest {
  @override
  final int amountInCents;
  @override
  final String borrower;
  @override
  final String date;
  @override
  final String description;
  @override
  final String id;
  @override
  final String paidFrom;
  @override
  final LendRequestType type;

  const MovementRequestLendRequest({
    required this.amountInCents,
    required this.borrower,
    required this.date,
    required this.description,
    required this.id,
    required this.paidFrom,
    required this.type,
  });

  factory MovementRequestLendRequest.fromJson(Map<String, dynamic> json) =>
      _$MovementRequestLendRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() => _$MovementRequestLendRequestToJson(this);
}

@JsonSerializable()
class MovementRequestOpeningBalanceRequest extends MovementRequest
    implements OpeningBalanceRequest {
  @override
  final String account;
  @override
  final int amountInCents;
  @override
  final String date;
  @override
  final String equity;
  @override
  final String id;
  @override
  final OpeningBalanceRequestType type;

  const MovementRequestOpeningBalanceRequest({
    required this.account,
    required this.amountInCents,
    required this.date,
    required this.equity,
    required this.id,
    required this.type,
  });

  factory MovementRequestOpeningBalanceRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$MovementRequestOpeningBalanceRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() =>
      _$MovementRequestOpeningBalanceRequestToJson(this);
}

@JsonSerializable()
class MovementRequestReceiveThirdPartyMoneyRequest extends MovementRequest
    implements ReceiveThirdPartyMoneyRequest {
  @override
  final int amountInCents;
  @override
  final String date;
  @override
  final String description;
  @override
  final String fund;
  @override
  final String heldIn;
  @override
  final String id;
  @override
  final ReceiveThirdPartyMoneyRequestType type;

  const MovementRequestReceiveThirdPartyMoneyRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.fund,
    required this.heldIn,
    required this.id,
    required this.type,
  });

  factory MovementRequestReceiveThirdPartyMoneyRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$MovementRequestReceiveThirdPartyMoneyRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() =>
      _$MovementRequestReceiveThirdPartyMoneyRequestToJson(this);
}

@JsonSerializable()
class MovementRequestRepayDebtRequest extends MovementRequest
    implements RepayDebtRequest {
  @override
  final int amountInCents;
  @override
  final String date;
  @override
  final String description;
  @override
  final String id;
  @override
  final String lender;
  @override
  final String paidFrom;
  @override
  final RepayDebtRequestType type;

  const MovementRequestRepayDebtRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.id,
    required this.lender,
    required this.paidFrom,
    required this.type,
  });

  factory MovementRequestRepayDebtRequest.fromJson(Map<String, dynamic> json) =>
      _$MovementRequestRepayDebtRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() =>
      _$MovementRequestRepayDebtRequestToJson(this);
}

@JsonSerializable()
class MovementRequestReturnThirdPartyMoneyRequest extends MovementRequest
    implements ReturnThirdPartyMoneyRequest {
  @override
  final int amountInCents;
  @override
  final String date;
  @override
  final String description;
  @override
  final String fund;
  @override
  final String id;
  @override
  final String paidFrom;
  @override
  final ReturnThirdPartyMoneyRequestType type;

  const MovementRequestReturnThirdPartyMoneyRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.fund,
    required this.id,
    required this.paidFrom,
    required this.type,
  });

  factory MovementRequestReturnThirdPartyMoneyRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$MovementRequestReturnThirdPartyMoneyRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() =>
      _$MovementRequestReturnThirdPartyMoneyRequestToJson(this);
}

@JsonSerializable()
class MovementRequestSpendThirdPartyMoneyRequest extends MovementRequest
    implements SpendThirdPartyMoneyRequest {
  @override
  final int amountInCents;
  @override
  final String date;
  @override
  final String description;
  @override
  final String fund;
  @override
  final String id;
  @override
  final String paidFrom;
  @override
  final SpendThirdPartyMoneyRequestType type;

  const MovementRequestSpendThirdPartyMoneyRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.fund,
    required this.id,
    required this.paidFrom,
    required this.type,
  });

  factory MovementRequestSpendThirdPartyMoneyRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$MovementRequestSpendThirdPartyMoneyRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() =>
      _$MovementRequestSpendThirdPartyMoneyRequestToJson(this);
}

@JsonSerializable()
class MovementRequestTransferRequest extends MovementRequest
    implements TransferRequest {
  @override
  final int amountInCents;
  @override
  final String date;
  @override
  final String description;
  @override
  final String from;
  @override
  final String id;
  @override
  final String to;
  @override
  final TransferRequestType type;

  const MovementRequestTransferRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.from,
    required this.id,
    required this.to,
    required this.type,
  });

  factory MovementRequestTransferRequest.fromJson(Map<String, dynamic> json) =>
      _$MovementRequestTransferRequestFromJson(json);

  @override
  Map<String, dynamic> toJson() => _$MovementRequestTransferRequestToJson(this);
}

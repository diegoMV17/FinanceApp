// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'open_account_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpenAccountRequest _$OpenAccountRequestFromJson(Map<String, dynamic> json) =>
    OpenAccountRequest(
      id: json['id'] as String,
      name: json['name'] as String,
      type: AccountType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$OpenAccountRequestToJson(OpenAccountRequest instance) =>
    <String, dynamic>{
      'id': instance.id,
      'name': instance.name,
      'type': _$AccountTypeEnumMap[instance.type]!,
    };

const _$AccountTypeEnumMap = {
  AccountType.asset: 'ASSET',
  AccountType.receivable: 'RECEIVABLE',
  AccountType.payable: 'PAYABLE',
  AccountType.thirdPartyFund: 'THIRD_PARTY_FUND',
  AccountType.income: 'INCOME',
  AccountType.expense: 'EXPENSE',
  AccountType.equity: 'EQUITY',
  AccountType.$unknown: r'$unknown',
};

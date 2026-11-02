// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'account_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AccountResponse _$AccountResponseFromJson(Map<String, dynamic> json) =>
    AccountResponse(
      archivedAt: json['archivedAt'] == null
          ? null
          : DateTime.parse(json['archivedAt'] as String),
      id: json['id'] as String?,
      name: json['name'] as String?,
      type: json['type'] == null
          ? null
          : AccountType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$AccountResponseToJson(AccountResponse instance) =>
    <String, dynamic>{
      'archivedAt': instance.archivedAt?.toIso8601String(),
      'id': instance.id,
      'name': instance.name,
      'type': _$AccountTypeEnumMap[instance.type],
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

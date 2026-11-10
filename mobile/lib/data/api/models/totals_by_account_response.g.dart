// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'totals_by_account_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TotalsByAccountResponse _$TotalsByAccountResponseFromJson(
  Map<String, dynamic> json,
) => TotalsByAccountResponse(
  accounts: (json['accounts'] as List<dynamic>)
      .map((e) => AccountTotalResponse.fromJson(e as Map<String, dynamic>))
      .toList(),
  from: json['from'] as String,
  to: json['to'] as String,
);

Map<String, dynamic> _$TotalsByAccountResponseToJson(
  TotalsByAccountResponse instance,
) => <String, dynamic>{
  'accounts': instance.accounts,
  'from': instance.from,
  'to': instance.to,
};

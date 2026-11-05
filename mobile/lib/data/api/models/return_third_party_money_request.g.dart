// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'return_third_party_money_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ReturnThirdPartyMoneyRequest _$ReturnThirdPartyMoneyRequestFromJson(
  Map<String, dynamic> json,
) => ReturnThirdPartyMoneyRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  fund: json['fund'] as String,
  id: json['id'] as String,
  paidFrom: json['paidFrom'] as String,
  type: ReturnThirdPartyMoneyRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$ReturnThirdPartyMoneyRequestToJson(
  ReturnThirdPartyMoneyRequest instance,
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

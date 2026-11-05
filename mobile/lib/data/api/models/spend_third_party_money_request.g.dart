// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'spend_third_party_money_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SpendThirdPartyMoneyRequest _$SpendThirdPartyMoneyRequestFromJson(
  Map<String, dynamic> json,
) => SpendThirdPartyMoneyRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  fund: json['fund'] as String,
  id: json['id'] as String,
  paidFrom: json['paidFrom'] as String,
  type: SpendThirdPartyMoneyRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$SpendThirdPartyMoneyRequestToJson(
  SpendThirdPartyMoneyRequest instance,
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

// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'receive_third_party_money_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ReceiveThirdPartyMoneyRequest _$ReceiveThirdPartyMoneyRequestFromJson(
  Map<String, dynamic> json,
) => ReceiveThirdPartyMoneyRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  description: json['description'] as String,
  fund: json['fund'] as String,
  heldIn: json['heldIn'] as String,
  id: json['id'] as String,
  type: ReceiveThirdPartyMoneyRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$ReceiveThirdPartyMoneyRequestToJson(
  ReceiveThirdPartyMoneyRequest instance,
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

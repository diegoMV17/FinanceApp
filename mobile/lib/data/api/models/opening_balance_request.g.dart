// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'opening_balance_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpeningBalanceRequest _$OpeningBalanceRequestFromJson(
  Map<String, dynamic> json,
) => OpeningBalanceRequest(
  account: json['account'] as String,
  amountInCents: (json['amountInCents'] as num).toInt(),
  date: json['date'] as String,
  equity: json['equity'] as String,
  id: json['id'] as String,
  type: OpeningBalanceRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$OpeningBalanceRequestToJson(
  OpeningBalanceRequest instance,
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

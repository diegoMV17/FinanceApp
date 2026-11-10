// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'cancellation_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CancellationResponse _$CancellationResponseFromJson(
  Map<String, dynamic> json,
) => CancellationResponse(
  at: DateTime.parse(json['at'] as String),
  movement: json['movement'] as String,
  reason: json['reason'] as String,
  source: TransactionSource.fromJson(json['source'] as String),
);

Map<String, dynamic> _$CancellationResponseToJson(
  CancellationResponse instance,
) => <String, dynamic>{
  'at': instance.at.toIso8601String(),
  'movement': instance.movement,
  'reason': instance.reason,
  'source': _$TransactionSourceEnumMap[instance.source]!,
};

const _$TransactionSourceEnumMap = {
  TransactionSource.app: 'APP',
  TransactionSource.bot: 'BOT',
  TransactionSource.recurring: 'RECURRING',
  TransactionSource.system: 'SYSTEM',
  TransactionSource.$unknown: r'$unknown',
};

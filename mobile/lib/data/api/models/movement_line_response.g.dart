// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'movement_line_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MovementLineResponse _$MovementLineResponseFromJson(
  Map<String, dynamic> json,
) => MovementLineResponse(
  effectInCents: (json['effectInCents'] as num).toInt(),
  date: json['date'] as String?,
  description: json['description'] as String?,
  id: json['id'] as String?,
  source: json['source'] == null
      ? null
      : TransactionSource.fromJson(json['source'] as String),
);

Map<String, dynamic> _$MovementLineResponseToJson(
  MovementLineResponse instance,
) => <String, dynamic>{
  'date': instance.date,
  'description': instance.description,
  'effectInCents': instance.effectInCents,
  'id': instance.id,
  'source': _$TransactionSourceEnumMap[instance.source],
};

const _$TransactionSourceEnumMap = {
  TransactionSource.app: 'APP',
  TransactionSource.bot: 'BOT',
  TransactionSource.recurring: 'RECURRING',
  TransactionSource.system: 'SYSTEM',
  TransactionSource.$unknown: r'$unknown',
};

// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'movement_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MovementResponse _$MovementResponseFromJson(Map<String, dynamic> json) =>
    MovementResponse(
      cancellationReason: json['cancellationReason'] as String?,
      cancelledAt: json['cancelledAt'] == null
          ? null
          : DateTime.parse(json['cancelledAt'] as String),
      date: json['date'] as String?,
      description: json['description'] as String?,
      entries: (json['entries'] as List<dynamic>?)
          ?.map((e) => EntryResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      id: json['id'] as String?,
      replaces: json['replaces'] as String?,
      source: json['source'] == null
          ? null
          : TransactionSource.fromJson(json['source'] as String),
    );

Map<String, dynamic> _$MovementResponseToJson(MovementResponse instance) =>
    <String, dynamic>{
      'cancellationReason': instance.cancellationReason,
      'cancelledAt': instance.cancelledAt?.toIso8601String(),
      'date': instance.date,
      'description': instance.description,
      'entries': instance.entries,
      'id': instance.id,
      'replaces': instance.replaces,
      'source': _$TransactionSourceEnumMap[instance.source],
    };

const _$TransactionSourceEnumMap = {
  TransactionSource.app: 'APP',
  TransactionSource.bot: 'BOT',
  TransactionSource.recurring: 'RECURRING',
  TransactionSource.system: 'SYSTEM',
  TransactionSource.$unknown: r'$unknown',
};

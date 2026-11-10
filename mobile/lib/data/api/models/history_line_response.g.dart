// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'history_line_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

HistoryLineResponse _$HistoryLineResponseFromJson(Map<String, dynamic> json) =>
    HistoryLineResponse(
      date: json['date'] as String,
      description: json['description'] as String,
      entries: (json['entries'] as List<dynamic>)
          .map((e) => EntryResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      id: json['id'] as String,
      source: TransactionSource.fromJson(json['source'] as String),
      valueInCents: (json['valueInCents'] as num).toInt(),
    );

Map<String, dynamic> _$HistoryLineResponseToJson(
  HistoryLineResponse instance,
) => <String, dynamic>{
  'date': instance.date,
  'description': instance.description,
  'entries': instance.entries,
  'id': instance.id,
  'source': _$TransactionSourceEnumMap[instance.source]!,
  'valueInCents': instance.valueInCents,
};

const _$TransactionSourceEnumMap = {
  TransactionSource.app: 'APP',
  TransactionSource.bot: 'BOT',
  TransactionSource.recurring: 'RECURRING',
  TransactionSource.system: 'SYSTEM',
  TransactionSource.$unknown: r'$unknown',
};

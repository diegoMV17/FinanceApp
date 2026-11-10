// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'over_time_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OverTimeResponse _$OverTimeResponseFromJson(Map<String, dynamic> json) =>
    OverTimeResponse(
      from: json['from'] as String,
      points: (json['points'] as List<dynamic>)
          .map((e) => SummaryResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      to: json['to'] as String,
    );

Map<String, dynamic> _$OverTimeResponseToJson(OverTimeResponse instance) =>
    <String, dynamic>{
      'from': instance.from,
      'points': instance.points,
      'to': instance.to,
    };

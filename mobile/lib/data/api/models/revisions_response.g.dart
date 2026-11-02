// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'revisions_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

RevisionsResponse _$RevisionsResponseFromJson(Map<String, dynamic> json) =>
    RevisionsResponse(
      movement: json['movement'] as String?,
      revisions: (json['revisions'] as List<dynamic>?)
          ?.map((e) => MovementResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$RevisionsResponseToJson(RevisionsResponse instance) =>
    <String, dynamic>{
      'movement': instance.movement,
      'revisions': instance.revisions,
    };

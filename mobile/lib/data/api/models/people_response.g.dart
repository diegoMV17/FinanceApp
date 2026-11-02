// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'people_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PeopleResponse _$PeopleResponseFromJson(Map<String, dynamic> json) =>
    PeopleResponse(
      asOf: json['asOf'] as String?,
      people: (json['people'] as List<dynamic>?)
          ?.map(
            (e) => PersonPositionResponse.fromJson(e as Map<String, dynamic>),
          )
          .toList(),
    );

Map<String, dynamic> _$PeopleResponseToJson(PeopleResponse instance) =>
    <String, dynamic>{'asOf': instance.asOf, 'people': instance.people};

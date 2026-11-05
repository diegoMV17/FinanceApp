// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'person_detail_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PersonDetailResponse _$PersonDetailResponseFromJson(
  Map<String, dynamic> json,
) => PersonDetailResponse(
  asOf: json['asOf'] as String?,
  person: json['person'] == null
      ? null
      : PersonPositionResponse.fromJson(json['person'] as Map<String, dynamic>),
);

Map<String, dynamic> _$PersonDetailResponseToJson(
  PersonDetailResponse instance,
) => <String, dynamic>{'asOf': instance.asOf, 'person': instance.person};

// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'person_position_response.dart';

part 'people_response.g.dart';

@JsonSerializable()
class PeopleResponse {
  const PeopleResponse({required this.asOf, required this.people});

  factory PeopleResponse.fromJson(Map<String, Object?> json) =>
      _$PeopleResponseFromJson(json);

  final String asOf;
  final List<PersonPositionResponse> people;

  Map<String, Object?> toJson() => _$PeopleResponseToJson(this);
}

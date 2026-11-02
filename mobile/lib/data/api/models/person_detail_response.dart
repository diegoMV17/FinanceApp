// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'person_position_response.dart';

part 'person_detail_response.g.dart';

@JsonSerializable()
class PersonDetailResponse {
  const PersonDetailResponse({this.asOf, this.person});

  factory PersonDetailResponse.fromJson(Map<String, Object?> json) =>
      _$PersonDetailResponseFromJson(json);

  final String? asOf;
  final PersonPositionResponse? person;

  Map<String, Object?> toJson() => _$PersonDetailResponseToJson(this);
}

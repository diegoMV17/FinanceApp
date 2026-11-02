// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'person_response.g.dart';

@JsonSerializable()
class PersonResponse {
  const PersonResponse({this.archivedAt, this.id, this.name});

  factory PersonResponse.fromJson(Map<String, Object?> json) =>
      _$PersonResponseFromJson(json);

  final DateTime? archivedAt;
  final String? id;
  final String? name;

  Map<String, Object?> toJson() => _$PersonResponseToJson(this);
}

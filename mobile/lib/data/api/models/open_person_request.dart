// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'open_person_request.g.dart';

@JsonSerializable()
class OpenPersonRequest {
  const OpenPersonRequest({required this.id, required this.name});

  factory OpenPersonRequest.fromJson(Map<String, Object?> json) =>
      _$OpenPersonRequestFromJson(json);

  final String id;
  final String name;

  Map<String, Object?> toJson() => _$OpenPersonRequestToJson(this);
}

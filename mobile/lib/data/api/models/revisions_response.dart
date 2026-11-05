// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'movement_response.dart';

part 'revisions_response.g.dart';

@JsonSerializable()
class RevisionsResponse {
  const RevisionsResponse({this.movement, this.revisions});

  factory RevisionsResponse.fromJson(Map<String, Object?> json) =>
      _$RevisionsResponseFromJson(json);

  final String? movement;
  final List<MovementResponse>? revisions;

  Map<String, Object?> toJson() => _$RevisionsResponseToJson(this);
}

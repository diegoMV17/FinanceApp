// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'movement_request.dart';

part 'amend_movement_request.g.dart';

@JsonSerializable()
class AmendMovementRequest {
  const AmendMovementRequest({required this.correction});

  factory AmendMovementRequest.fromJson(Map<String, Object?> json) =>
      _$AmendMovementRequestFromJson(json);

  final MovementRequest correction;

  Map<String, Object?> toJson() => _$AmendMovementRequestToJson(this);
}

// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'cancel_movement_request.g.dart';

@JsonSerializable()
class CancelMovementRequest {
  const CancelMovementRequest({required this.reason});

  factory CancelMovementRequest.fromJson(Map<String, Object?> json) =>
      _$CancelMovementRequestFromJson(json);

  final String reason;

  Map<String, Object?> toJson() => _$CancelMovementRequestToJson(this);
}

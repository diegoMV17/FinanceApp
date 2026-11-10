// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'transaction_source.dart';

part 'cancellation_response.g.dart';

@JsonSerializable()
class CancellationResponse {
  const CancellationResponse({
    required this.at,
    required this.movement,
    required this.reason,
    required this.source,
  });

  factory CancellationResponse.fromJson(Map<String, Object?> json) =>
      _$CancellationResponseFromJson(json);

  final DateTime at;
  final String movement;
  final String reason;
  final TransactionSource source;

  Map<String, Object?> toJson() => _$CancellationResponseToJson(this);
}

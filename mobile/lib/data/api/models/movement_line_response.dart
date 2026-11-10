// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'transaction_source.dart';

part 'movement_line_response.g.dart';

@JsonSerializable()
class MovementLineResponse {
  const MovementLineResponse({
    required this.date,
    required this.description,
    required this.effectInCents,
    required this.id,
    required this.source,
  });

  factory MovementLineResponse.fromJson(Map<String, Object?> json) =>
      _$MovementLineResponseFromJson(json);

  final String date;
  final String description;
  final int effectInCents;
  final String id;
  final TransactionSource source;

  Map<String, Object?> toJson() => _$MovementLineResponseToJson(this);
}

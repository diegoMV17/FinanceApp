// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'entry_response.dart';
import 'transaction_source.dart';

part 'movement_response.g.dart';

@JsonSerializable()
class MovementResponse {
  const MovementResponse({
    required this.date,
    required this.description,
    required this.entries,
    required this.id,
    required this.source,
    this.cancellationReason,
    this.cancelledAt,
    this.replaces,
  });

  factory MovementResponse.fromJson(Map<String, Object?> json) =>
      _$MovementResponseFromJson(json);

  final String? cancellationReason;
  final DateTime? cancelledAt;
  final String date;
  final String description;
  final List<EntryResponse> entries;
  final String id;
  final String? replaces;
  final TransactionSource source;

  Map<String, Object?> toJson() => _$MovementResponseToJson(this);
}

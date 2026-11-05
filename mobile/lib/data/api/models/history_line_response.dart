// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'entry_response.dart';
import 'transaction_source.dart';

part 'history_line_response.g.dart';

@JsonSerializable()
class HistoryLineResponse {
  const HistoryLineResponse({
    required this.valueInCents,
    this.date,
    this.description,
    this.entries,
    this.id,
    this.source,
  });

  factory HistoryLineResponse.fromJson(Map<String, Object?> json) =>
      _$HistoryLineResponseFromJson(json);

  final String? date;
  final String? description;
  final List<EntryResponse>? entries;
  final String? id;
  final TransactionSource? source;
  final int valueInCents;

  Map<String, Object?> toJson() => _$HistoryLineResponseToJson(this);
}

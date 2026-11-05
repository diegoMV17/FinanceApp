// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'history_line_response.dart';

part 'history_response.g.dart';

@JsonSerializable()
class HistoryResponse {
  const HistoryResponse({
    required this.page,
    required this.size,
    required this.total,
    this.movements,
  });

  factory HistoryResponse.fromJson(Map<String, Object?> json) =>
      _$HistoryResponseFromJson(json);

  final List<HistoryLineResponse>? movements;
  final int page;
  final int size;
  final int total;

  Map<String, Object?> toJson() => _$HistoryResponseToJson(this);
}

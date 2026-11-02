// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'summary_response.dart';

part 'over_time_response.g.dart';

@JsonSerializable()
class OverTimeResponse {
  const OverTimeResponse({this.from, this.points, this.to});

  factory OverTimeResponse.fromJson(Map<String, Object?> json) =>
      _$OverTimeResponseFromJson(json);

  final String? from;
  final List<SummaryResponse>? points;
  final String? to;

  Map<String, Object?> toJson() => _$OverTimeResponseToJson(this);
}

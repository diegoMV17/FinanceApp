// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'movement_line_response.dart';

part 'movements_response.g.dart';

@JsonSerializable()
class MovementsResponse {
  const MovementsResponse({
    required this.account,
    required this.movements,
    required this.page,
    required this.size,
    required this.total,
  });

  factory MovementsResponse.fromJson(Map<String, Object?> json) =>
      _$MovementsResponseFromJson(json);

  final String account;
  final List<MovementLineResponse> movements;
  final int page;
  final int size;
  final int total;

  Map<String, Object?> toJson() => _$MovementsResponseToJson(this);
}

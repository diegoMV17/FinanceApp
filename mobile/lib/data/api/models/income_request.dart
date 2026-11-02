// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'income_request_type.dart';
import 'movement_request.dart';

part 'income_request.g.dart';

@JsonSerializable()
class IncomeRequest {
  const IncomeRequest({
    required this.amountInCents,
    required this.category,
    required this.date,
    required this.description,
    required this.id,
    required this.receivedInto,
    required this.type,
  });

  factory IncomeRequest.fromJson(Map<String, Object?> json) =>
      _$IncomeRequestFromJson(json);

  final int amountInCents;
  final String category;
  final String date;
  final String description;
  final String id;
  final String receivedInto;
  final IncomeRequestType type;

  Map<String, Object?> toJson() => _$IncomeRequestToJson(this);
}

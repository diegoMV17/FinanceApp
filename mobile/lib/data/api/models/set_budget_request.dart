// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'set_budget_request.g.dart';

@JsonSerializable()
class SetBudgetRequest {
  const SetBudgetRequest({
    required this.category,
    required this.id,
    required this.limitInCents,
    required this.month,
  });

  factory SetBudgetRequest.fromJson(Map<String, Object?> json) =>
      _$SetBudgetRequestFromJson(json);

  final String category;
  final String id;
  final int limitInCents;
  final String month;

  Map<String, Object?> toJson() => _$SetBudgetRequestToJson(this);
}

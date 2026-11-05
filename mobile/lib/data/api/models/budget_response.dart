// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'budget_response.g.dart';

@JsonSerializable()
class BudgetResponse {
  const BudgetResponse({
    required this.limitInCents,
    this.category,
    this.categoryName,
    this.id,
    this.month,
    this.replaces,
  });

  factory BudgetResponse.fromJson(Map<String, Object?> json) =>
      _$BudgetResponseFromJson(json);

  final String? category;
  final String? categoryName;
  final String? id;
  final int limitInCents;
  final String? month;
  final String? replaces;

  Map<String, Object?> toJson() => _$BudgetResponseToJson(this);
}

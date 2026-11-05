// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'budget_progress_response.g.dart';

@JsonSerializable()
class BudgetProgressResponse {
  const BudgetProgressResponse({
    required this.exceeded,
    required this.limitInCents,
    required this.nearTheLimit,
    required this.percentageUsed,
    required this.remainingInCents,
    required this.spentInCents,
    this.category,
    this.categoryName,
    this.id,
    this.month,
  });

  factory BudgetProgressResponse.fromJson(Map<String, Object?> json) =>
      _$BudgetProgressResponseFromJson(json);

  final String? category;
  final String? categoryName;
  final bool exceeded;
  final String? id;
  final int limitInCents;
  final String? month;
  final bool nearTheLimit;
  final int percentageUsed;
  final int remainingInCents;
  final int spentInCents;

  Map<String, Object?> toJson() => _$BudgetProgressResponseToJson(this);
}

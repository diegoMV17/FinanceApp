// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'budget_progress_response.dart';

part 'budgets_response.g.dart';

@JsonSerializable()
class BudgetsResponse {
  const BudgetsResponse({this.budgets, this.month});

  factory BudgetsResponse.fromJson(Map<String, Object?> json) =>
      _$BudgetsResponseFromJson(json);

  final List<BudgetProgressResponse>? budgets;
  final String? month;

  Map<String, Object?> toJson() => _$BudgetsResponseToJson(this);
}

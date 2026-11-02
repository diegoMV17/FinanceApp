// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'summary_response.g.dart';

@JsonSerializable()
class SummaryResponse {
  const SummaryResponse({
    required this.expensesThatMonthInCents,
    required this.incomeThatMonthInCents,
    required this.moneyReallyMineInCents,
    this.asOf,
    this.monthFrom,
    this.totalsByTypeInCents,
  });

  factory SummaryResponse.fromJson(Map<String, Object?> json) =>
      _$SummaryResponseFromJson(json);

  final String? asOf;
  final int expensesThatMonthInCents;
  final int incomeThatMonthInCents;
  final int moneyReallyMineInCents;
  final String? monthFrom;
  final Map<String, int>? totalsByTypeInCents;

  Map<String, Object?> toJson() => _$SummaryResponseToJson(this);
}

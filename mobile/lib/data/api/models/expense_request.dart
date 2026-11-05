// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'expense_request_type.dart';
import 'movement_request.dart';

part 'expense_request.g.dart';

@JsonSerializable()
class ExpenseRequest {
  const ExpenseRequest({
    required this.amountInCents,
    required this.category,
    required this.date,
    required this.description,
    required this.id,
    required this.paidFrom,
    required this.type,
  });

  factory ExpenseRequest.fromJson(Map<String, Object?> json) =>
      _$ExpenseRequestFromJson(json);

  final int amountInCents;
  final String category;
  final String date;
  final String description;
  final String id;
  final String paidFrom;
  final ExpenseRequestType type;

  Map<String, Object?> toJson() => _$ExpenseRequestToJson(this);
}

// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'movement_request.dart';
import 'repay_debt_request_type.dart';

part 'repay_debt_request.g.dart';

@JsonSerializable()
class RepayDebtRequest {
  const RepayDebtRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.id,
    required this.lender,
    required this.paidFrom,
    required this.type,
  });

  factory RepayDebtRequest.fromJson(Map<String, Object?> json) =>
      _$RepayDebtRequestFromJson(json);

  final int amountInCents;
  final String date;
  final String description;
  final String id;
  final String lender;
  final String paidFrom;
  final RepayDebtRequestType type;

  Map<String, Object?> toJson() => _$RepayDebtRequestToJson(this);
}

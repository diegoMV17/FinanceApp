// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'collect_loan_request_type.dart';
import 'movement_request.dart';

part 'collect_loan_request.g.dart';

@JsonSerializable()
class CollectLoanRequest {
  const CollectLoanRequest({
    required this.amountInCents,
    required this.borrower,
    required this.date,
    required this.description,
    required this.id,
    required this.receivedInto,
    required this.type,
  });

  factory CollectLoanRequest.fromJson(Map<String, Object?> json) =>
      _$CollectLoanRequestFromJson(json);

  final int amountInCents;
  final String borrower;
  final String date;
  final String description;
  final String id;
  final String receivedInto;
  final CollectLoanRequestType type;

  Map<String, Object?> toJson() => _$CollectLoanRequestToJson(this);
}

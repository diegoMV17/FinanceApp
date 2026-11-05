// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'lend_request_type.dart';
import 'movement_request.dart';

part 'lend_request.g.dart';

@JsonSerializable()
class LendRequest {
  const LendRequest({
    required this.amountInCents,
    required this.borrower,
    required this.date,
    required this.description,
    required this.id,
    required this.paidFrom,
    required this.type,
  });

  factory LendRequest.fromJson(Map<String, Object?> json) =>
      _$LendRequestFromJson(json);

  final int amountInCents;
  final String borrower;
  final String date;
  final String description;
  final String id;
  final String paidFrom;
  final LendRequestType type;

  Map<String, Object?> toJson() => _$LendRequestToJson(this);
}

// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'movement_request.dart';
import 'opening_balance_request_type.dart';

part 'opening_balance_request.g.dart';

@JsonSerializable()
class OpeningBalanceRequest {
  const OpeningBalanceRequest({
    required this.account,
    required this.amountInCents,
    required this.date,
    required this.equity,
    required this.id,
    required this.type,
  });

  factory OpeningBalanceRequest.fromJson(Map<String, Object?> json) =>
      _$OpeningBalanceRequestFromJson(json);

  final String account;
  final int amountInCents;
  final String date;
  final String equity;
  final String id;
  final OpeningBalanceRequestType type;

  Map<String, Object?> toJson() => _$OpeningBalanceRequestToJson(this);
}

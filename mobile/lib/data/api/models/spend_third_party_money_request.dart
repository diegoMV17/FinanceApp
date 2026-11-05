// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'movement_request.dart';
import 'spend_third_party_money_request_type.dart';

part 'spend_third_party_money_request.g.dart';

@JsonSerializable()
class SpendThirdPartyMoneyRequest {
  const SpendThirdPartyMoneyRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.fund,
    required this.id,
    required this.paidFrom,
    required this.type,
  });

  factory SpendThirdPartyMoneyRequest.fromJson(Map<String, Object?> json) =>
      _$SpendThirdPartyMoneyRequestFromJson(json);

  final int amountInCents;
  final String date;
  final String description;
  final String fund;
  final String id;
  final String paidFrom;
  final SpendThirdPartyMoneyRequestType type;

  Map<String, Object?> toJson() => _$SpendThirdPartyMoneyRequestToJson(this);
}

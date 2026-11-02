// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'movement_request.dart';
import 'receive_third_party_money_request_type.dart';

part 'receive_third_party_money_request.g.dart';

@JsonSerializable()
class ReceiveThirdPartyMoneyRequest {
  const ReceiveThirdPartyMoneyRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.fund,
    required this.heldIn,
    required this.id,
    required this.type,
  });

  factory ReceiveThirdPartyMoneyRequest.fromJson(Map<String, Object?> json) =>
      _$ReceiveThirdPartyMoneyRequestFromJson(json);

  final int amountInCents;
  final String date;
  final String description;
  final String fund;
  final String heldIn;
  final String id;
  final ReceiveThirdPartyMoneyRequestType type;

  Map<String, Object?> toJson() => _$ReceiveThirdPartyMoneyRequestToJson(this);
}

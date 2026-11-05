// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'balance_response.dart';

part 'balances_response.g.dart';

@JsonSerializable()
class BalancesResponse {
  const BalancesResponse({this.accounts, this.asOf});

  factory BalancesResponse.fromJson(Map<String, Object?> json) =>
      _$BalancesResponseFromJson(json);

  final List<BalanceResponse>? accounts;
  final String? asOf;

  Map<String, Object?> toJson() => _$BalancesResponseToJson(this);
}

// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'account_type.dart';

part 'balance_response.g.dart';

@JsonSerializable()
class BalanceResponse {
  const BalanceResponse({
    required this.balanceInCents,
    this.archivedAt,
    this.id,
    this.name,
    this.type,
  });

  factory BalanceResponse.fromJson(Map<String, Object?> json) =>
      _$BalanceResponseFromJson(json);

  final DateTime? archivedAt;
  final int balanceInCents;
  final String? id;
  final String? name;
  final AccountType? type;

  Map<String, Object?> toJson() => _$BalanceResponseToJson(this);
}

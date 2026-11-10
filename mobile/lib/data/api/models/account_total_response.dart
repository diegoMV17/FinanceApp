// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'account_type.dart';

part 'account_total_response.g.dart';

@JsonSerializable()
class AccountTotalResponse {
  const AccountTotalResponse({
    required this.id,
    required this.name,
    required this.totalInCents,
    required this.type,
    this.archivedAt,
  });

  factory AccountTotalResponse.fromJson(Map<String, Object?> json) =>
      _$AccountTotalResponseFromJson(json);

  final DateTime? archivedAt;
  final String id;
  final String name;
  final int totalInCents;
  final AccountType type;

  Map<String, Object?> toJson() => _$AccountTotalResponseToJson(this);
}

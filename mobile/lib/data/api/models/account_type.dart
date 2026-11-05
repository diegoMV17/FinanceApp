// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

@JsonEnum()
enum AccountType {
  @JsonValue('ASSET')
  asset('ASSET'),
  @JsonValue('RECEIVABLE')
  receivable('RECEIVABLE'),
  @JsonValue('PAYABLE')
  payable('PAYABLE'),
  @JsonValue('THIRD_PARTY_FUND')
  thirdPartyFund('THIRD_PARTY_FUND'),
  @JsonValue('INCOME')
  income('INCOME'),
  @JsonValue('EXPENSE')
  expense('EXPENSE'),
  @JsonValue('EQUITY')
  equity('EQUITY'),

  /// Default value for all unparsed values, allows backward compatibility when adding new values on the backend.
  $unknown(null);

  const AccountType(this.json);

  factory AccountType.fromJson(String json) =>
      values.firstWhere((e) => e.json == json, orElse: () => $unknown);

  final String? json;

  @override
  String toString() => json?.toString() ?? super.toString();

  /// Returns all defined enum values excluding the $unknown value.
  static List<AccountType> get $valuesDefined =>
      values.where((value) => value != $unknown).toList();
}

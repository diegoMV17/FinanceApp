// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'account_total_response.dart';

part 'totals_by_account_response.g.dart';

@JsonSerializable()
class TotalsByAccountResponse {
  const TotalsByAccountResponse({this.accounts, this.from, this.to});

  factory TotalsByAccountResponse.fromJson(Map<String, Object?> json) =>
      _$TotalsByAccountResponseFromJson(json);

  final List<AccountTotalResponse>? accounts;
  final String? from;
  final String? to;

  Map<String, Object?> toJson() => _$TotalsByAccountResponseToJson(this);
}

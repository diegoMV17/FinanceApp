// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'account_type.dart';

part 'account_response.g.dart';

@JsonSerializable()
class AccountResponse {
  const AccountResponse({this.archivedAt, this.id, this.name, this.type});

  factory AccountResponse.fromJson(Map<String, Object?> json) =>
      _$AccountResponseFromJson(json);

  final DateTime? archivedAt;
  final String? id;
  final String? name;
  final AccountType? type;

  Map<String, Object?> toJson() => _$AccountResponseToJson(this);
}

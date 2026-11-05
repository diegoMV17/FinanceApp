// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'account_type.dart';

part 'open_account_request.g.dart';

@JsonSerializable()
class OpenAccountRequest {
  const OpenAccountRequest({
    required this.id,
    required this.name,
    required this.type,
  });

  factory OpenAccountRequest.fromJson(Map<String, Object?> json) =>
      _$OpenAccountRequestFromJson(json);

  final String id;
  final String name;
  final AccountType type;

  Map<String, Object?> toJson() => _$OpenAccountRequestToJson(this);
}

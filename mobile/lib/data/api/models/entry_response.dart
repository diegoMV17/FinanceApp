// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'entry_response.g.dart';

@JsonSerializable()
class EntryResponse {
  const EntryResponse({
    required this.account,
    required this.accountName,
    required this.amountInCents,
  });

  factory EntryResponse.fromJson(Map<String, Object?> json) =>
      _$EntryResponseFromJson(json);

  final String account;
  final String accountName;
  final int amountInCents;

  Map<String, Object?> toJson() => _$EntryResponseToJson(this);
}

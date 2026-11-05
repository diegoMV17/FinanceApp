// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'link_account_request.g.dart';

@JsonSerializable()
class LinkAccountRequest {
  const LinkAccountRequest({required this.account});

  factory LinkAccountRequest.fromJson(Map<String, Object?> json) =>
      _$LinkAccountRequestFromJson(json);

  final String account;

  Map<String, Object?> toJson() => _$LinkAccountRequestToJson(this);
}

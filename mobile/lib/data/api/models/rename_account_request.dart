// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'rename_account_request.g.dart';

@JsonSerializable()
class RenameAccountRequest {
  const RenameAccountRequest({required this.newName});

  factory RenameAccountRequest.fromJson(Map<String, Object?> json) =>
      _$RenameAccountRequestFromJson(json);

  final String newName;

  Map<String, Object?> toJson() => _$RenameAccountRequestToJson(this);
}

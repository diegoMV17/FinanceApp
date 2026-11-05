// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

part 'rename_person_request.g.dart';

@JsonSerializable()
class RenamePersonRequest {
  const RenamePersonRequest({required this.newName});

  factory RenamePersonRequest.fromJson(Map<String, Object?> json) =>
      _$RenamePersonRequestFromJson(json);

  final String newName;

  Map<String, Object?> toJson() => _$RenamePersonRequestToJson(this);
}

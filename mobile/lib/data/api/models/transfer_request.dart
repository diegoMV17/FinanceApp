// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'movement_request.dart';
import 'transfer_request_type.dart';

part 'transfer_request.g.dart';

@JsonSerializable()
class TransferRequest {
  const TransferRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.from,
    required this.id,
    required this.to,
    required this.type,
  });

  factory TransferRequest.fromJson(Map<String, Object?> json) =>
      _$TransferRequestFromJson(json);

  final int amountInCents;
  final String date;
  final String description;
  final String from;
  final String id;
  final String to;
  final TransferRequestType type;

  Map<String, Object?> toJson() => _$TransferRequestToJson(this);
}

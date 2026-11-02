// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'borrow_request_type.dart';
import 'movement_request.dart';

part 'borrow_request.g.dart';

@JsonSerializable()
class BorrowRequest {
  const BorrowRequest({
    required this.amountInCents,
    required this.date,
    required this.description,
    required this.id,
    required this.lender,
    required this.receivedInto,
    required this.type,
  });

  factory BorrowRequest.fromJson(Map<String, Object?> json) =>
      _$BorrowRequestFromJson(json);

  final int amountInCents;
  final String date;
  final String description;
  final String id;
  final String lender;
  final String receivedInto;
  final BorrowRequestType type;

  Map<String, Object?> toJson() => _$BorrowRequestToJson(this);
}

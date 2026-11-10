// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'balance_response.dart';

part 'person_position_response.g.dart';

@JsonSerializable()
class PersonPositionResponse {
  const PersonPositionResponse({
    required this.accounts,
    required this.id,
    required this.name,
    required this.netOwedToYouInCents,
    required this.owedToYouInCents,
    required this.theirFundInCents,
    required this.youOweInCents,
    this.archivedAt,
  });

  factory PersonPositionResponse.fromJson(Map<String, Object?> json) =>
      _$PersonPositionResponseFromJson(json);

  final List<BalanceResponse> accounts;
  final DateTime? archivedAt;
  final String id;
  final String name;
  final int netOwedToYouInCents;
  final int owedToYouInCents;
  final int theirFundInCents;
  final int youOweInCents;

  Map<String, Object?> toJson() => _$PersonPositionResponseToJson(this);
}

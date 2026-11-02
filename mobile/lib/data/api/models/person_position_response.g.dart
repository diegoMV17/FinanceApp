// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'person_position_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PersonPositionResponse _$PersonPositionResponseFromJson(
  Map<String, dynamic> json,
) => PersonPositionResponse(
  netOwedToYouInCents: (json['netOwedToYouInCents'] as num).toInt(),
  owedToYouInCents: (json['owedToYouInCents'] as num).toInt(),
  theirFundInCents: (json['theirFundInCents'] as num).toInt(),
  youOweInCents: (json['youOweInCents'] as num).toInt(),
  accounts: (json['accounts'] as List<dynamic>?)
      ?.map((e) => BalanceResponse.fromJson(e as Map<String, dynamic>))
      .toList(),
  archivedAt: json['archivedAt'] == null
      ? null
      : DateTime.parse(json['archivedAt'] as String),
  id: json['id'] as String?,
  name: json['name'] as String?,
);

Map<String, dynamic> _$PersonPositionResponseToJson(
  PersonPositionResponse instance,
) => <String, dynamic>{
  'accounts': instance.accounts,
  'archivedAt': instance.archivedAt?.toIso8601String(),
  'id': instance.id,
  'name': instance.name,
  'netOwedToYouInCents': instance.netOwedToYouInCents,
  'owedToYouInCents': instance.owedToYouInCents,
  'theirFundInCents': instance.theirFundInCents,
  'youOweInCents': instance.youOweInCents,
};

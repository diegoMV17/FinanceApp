// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'pay_card_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PayCardRequest _$PayCardRequestFromJson(Map<String, dynamic> json) =>
    PayCardRequest(
      amountInCents: (json['amountInCents'] as num).toInt(),
      card: json['card'] as String,
      date: json['date'] as String,
      description: json['description'] as String,
      from: json['from'] as String,
      id: json['id'] as String,
      type: PayCardRequestType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$PayCardRequestToJson(PayCardRequest instance) =>
    <String, dynamic>{
      'amountInCents': instance.amountInCents,
      'card': instance.card,
      'date': instance.date,
      'description': instance.description,
      'from': instance.from,
      'id': instance.id,
      'type': _$PayCardRequestTypeEnumMap[instance.type]!,
    };

const _$PayCardRequestTypeEnumMap = {
  PayCardRequestType.payCard: 'PAY_CARD',
  PayCardRequestType.$unknown: r'$unknown',
};

// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'income_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IncomeRequest _$IncomeRequestFromJson(Map<String, dynamic> json) =>
    IncomeRequest(
      amountInCents: (json['amountInCents'] as num).toInt(),
      category: json['category'] as String,
      date: json['date'] as String,
      description: json['description'] as String,
      id: json['id'] as String,
      receivedInto: json['receivedInto'] as String,
      type: IncomeRequestType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$IncomeRequestToJson(IncomeRequest instance) =>
    <String, dynamic>{
      'amountInCents': instance.amountInCents,
      'category': instance.category,
      'date': instance.date,
      'description': instance.description,
      'id': instance.id,
      'receivedInto': instance.receivedInto,
      'type': _$IncomeRequestTypeEnumMap[instance.type]!,
    };

const _$IncomeRequestTypeEnumMap = {
  IncomeRequestType.income: 'INCOME',
  IncomeRequestType.$unknown: r'$unknown',
};

// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'expense_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExpenseRequest _$ExpenseRequestFromJson(Map<String, dynamic> json) =>
    ExpenseRequest(
      amountInCents: (json['amountInCents'] as num).toInt(),
      category: json['category'] as String,
      date: json['date'] as String,
      description: json['description'] as String,
      id: json['id'] as String,
      paidFrom: json['paidFrom'] as String,
      type: ExpenseRequestType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$ExpenseRequestToJson(ExpenseRequest instance) =>
    <String, dynamic>{
      'amountInCents': instance.amountInCents,
      'category': instance.category,
      'date': instance.date,
      'description': instance.description,
      'id': instance.id,
      'paidFrom': instance.paidFrom,
      'type': _$ExpenseRequestTypeEnumMap[instance.type]!,
    };

const _$ExpenseRequestTypeEnumMap = {
  ExpenseRequestType.expense: 'EXPENSE',
  ExpenseRequestType.$unknown: r'$unknown',
};

// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'set_budget_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SetBudgetRequest _$SetBudgetRequestFromJson(Map<String, dynamic> json) =>
    SetBudgetRequest(
      category: json['category'] as String,
      id: json['id'] as String,
      limitInCents: (json['limitInCents'] as num).toInt(),
      month: json['month'] as String,
    );

Map<String, dynamic> _$SetBudgetRequestToJson(SetBudgetRequest instance) =>
    <String, dynamic>{
      'category': instance.category,
      'id': instance.id,
      'limitInCents': instance.limitInCents,
      'month': instance.month,
    };

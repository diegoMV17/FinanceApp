// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'budget_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BudgetResponse _$BudgetResponseFromJson(Map<String, dynamic> json) =>
    BudgetResponse(
      category: json['category'] as String,
      categoryName: json['categoryName'] as String,
      id: json['id'] as String,
      limitInCents: (json['limitInCents'] as num).toInt(),
      month: json['month'] as String,
      replaces: json['replaces'] as String?,
    );

Map<String, dynamic> _$BudgetResponseToJson(BudgetResponse instance) =>
    <String, dynamic>{
      'category': instance.category,
      'categoryName': instance.categoryName,
      'id': instance.id,
      'limitInCents': instance.limitInCents,
      'month': instance.month,
      'replaces': instance.replaces,
    };

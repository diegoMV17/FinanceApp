// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'budget_progress_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BudgetProgressResponse _$BudgetProgressResponseFromJson(
  Map<String, dynamic> json,
) => BudgetProgressResponse(
  exceeded: json['exceeded'] as bool,
  limitInCents: (json['limitInCents'] as num).toInt(),
  nearTheLimit: json['nearTheLimit'] as bool,
  percentageUsed: (json['percentageUsed'] as num).toInt(),
  remainingInCents: (json['remainingInCents'] as num).toInt(),
  spentInCents: (json['spentInCents'] as num).toInt(),
  category: json['category'] as String?,
  categoryName: json['categoryName'] as String?,
  id: json['id'] as String?,
  month: json['month'] as String?,
);

Map<String, dynamic> _$BudgetProgressResponseToJson(
  BudgetProgressResponse instance,
) => <String, dynamic>{
  'category': instance.category,
  'categoryName': instance.categoryName,
  'exceeded': instance.exceeded,
  'id': instance.id,
  'limitInCents': instance.limitInCents,
  'month': instance.month,
  'nearTheLimit': instance.nearTheLimit,
  'percentageUsed': instance.percentageUsed,
  'remainingInCents': instance.remainingInCents,
  'spentInCents': instance.spentInCents,
};

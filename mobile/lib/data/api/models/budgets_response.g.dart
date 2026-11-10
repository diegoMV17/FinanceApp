// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'budgets_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BudgetsResponse _$BudgetsResponseFromJson(Map<String, dynamic> json) =>
    BudgetsResponse(
      budgets: (json['budgets'] as List<dynamic>)
          .map(
            (e) => BudgetProgressResponse.fromJson(e as Map<String, dynamic>),
          )
          .toList(),
      month: json['month'] as String,
    );

Map<String, dynamic> _$BudgetsResponseToJson(BudgetsResponse instance) =>
    <String, dynamic>{'budgets': instance.budgets, 'month': instance.month};

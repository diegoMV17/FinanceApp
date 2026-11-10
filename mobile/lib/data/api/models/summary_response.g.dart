// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'summary_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SummaryResponse _$SummaryResponseFromJson(Map<String, dynamic> json) =>
    SummaryResponse(
      asOf: json['asOf'] as String,
      expensesThatMonthInCents: (json['expensesThatMonthInCents'] as num)
          .toInt(),
      incomeThatMonthInCents: (json['incomeThatMonthInCents'] as num).toInt(),
      moneyReallyMineInCents: (json['moneyReallyMineInCents'] as num).toInt(),
      monthFrom: json['monthFrom'] as String,
      totalsByTypeInCents: Map<String, int>.from(
        json['totalsByTypeInCents'] as Map,
      ),
    );

Map<String, dynamic> _$SummaryResponseToJson(SummaryResponse instance) =>
    <String, dynamic>{
      'asOf': instance.asOf,
      'expensesThatMonthInCents': instance.expensesThatMonthInCents,
      'incomeThatMonthInCents': instance.incomeThatMonthInCents,
      'moneyReallyMineInCents': instance.moneyReallyMineInCents,
      'monthFrom': instance.monthFrom,
      'totalsByTypeInCents': instance.totalsByTypeInCents,
    };

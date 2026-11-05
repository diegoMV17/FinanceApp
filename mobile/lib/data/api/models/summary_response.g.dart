// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'summary_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SummaryResponse _$SummaryResponseFromJson(
  Map<String, dynamic> json,
) => SummaryResponse(
  expensesThatMonthInCents: (json['expensesThatMonthInCents'] as num).toInt(),
  incomeThatMonthInCents: (json['incomeThatMonthInCents'] as num).toInt(),
  moneyReallyMineInCents: (json['moneyReallyMineInCents'] as num).toInt(),
  asOf: json['asOf'] as String?,
  monthFrom: json['monthFrom'] as String?,
  totalsByTypeInCents: (json['totalsByTypeInCents'] as Map<String, dynamic>?)
      ?.map((k, e) => MapEntry(k, (e as num).toInt())),
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

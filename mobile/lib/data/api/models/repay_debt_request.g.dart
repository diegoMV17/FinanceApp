// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'repay_debt_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

RepayDebtRequest _$RepayDebtRequestFromJson(Map<String, dynamic> json) =>
    RepayDebtRequest(
      amountInCents: (json['amountInCents'] as num).toInt(),
      date: json['date'] as String,
      description: json['description'] as String,
      id: json['id'] as String,
      lender: json['lender'] as String,
      paidFrom: json['paidFrom'] as String,
      type: RepayDebtRequestType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$RepayDebtRequestToJson(RepayDebtRequest instance) =>
    <String, dynamic>{
      'amountInCents': instance.amountInCents,
      'date': instance.date,
      'description': instance.description,
      'id': instance.id,
      'lender': instance.lender,
      'paidFrom': instance.paidFrom,
      'type': _$RepayDebtRequestTypeEnumMap[instance.type]!,
    };

const _$RepayDebtRequestTypeEnumMap = {
  RepayDebtRequestType.repayDebt: 'REPAY_DEBT',
  RepayDebtRequestType.$unknown: r'$unknown',
};

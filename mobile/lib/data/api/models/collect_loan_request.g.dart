// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'collect_loan_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CollectLoanRequest _$CollectLoanRequestFromJson(Map<String, dynamic> json) =>
    CollectLoanRequest(
      amountInCents: (json['amountInCents'] as num).toInt(),
      borrower: json['borrower'] as String,
      date: json['date'] as String,
      description: json['description'] as String,
      id: json['id'] as String,
      receivedInto: json['receivedInto'] as String,
      type: CollectLoanRequestType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$CollectLoanRequestToJson(CollectLoanRequest instance) =>
    <String, dynamic>{
      'amountInCents': instance.amountInCents,
      'borrower': instance.borrower,
      'date': instance.date,
      'description': instance.description,
      'id': instance.id,
      'receivedInto': instance.receivedInto,
      'type': _$CollectLoanRequestTypeEnumMap[instance.type]!,
    };

const _$CollectLoanRequestTypeEnumMap = {
  CollectLoanRequestType.collectLoan: 'COLLECT_LOAN',
  CollectLoanRequestType.$unknown: r'$unknown',
};

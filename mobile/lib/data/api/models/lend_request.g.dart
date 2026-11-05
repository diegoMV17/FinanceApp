// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'lend_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

LendRequest _$LendRequestFromJson(Map<String, dynamic> json) => LendRequest(
  amountInCents: (json['amountInCents'] as num).toInt(),
  borrower: json['borrower'] as String,
  date: json['date'] as String,
  description: json['description'] as String,
  id: json['id'] as String,
  paidFrom: json['paidFrom'] as String,
  type: LendRequestType.fromJson(json['type'] as String),
);

Map<String, dynamic> _$LendRequestToJson(LendRequest instance) =>
    <String, dynamic>{
      'amountInCents': instance.amountInCents,
      'borrower': instance.borrower,
      'date': instance.date,
      'description': instance.description,
      'id': instance.id,
      'paidFrom': instance.paidFrom,
      'type': _$LendRequestTypeEnumMap[instance.type]!,
    };

const _$LendRequestTypeEnumMap = {
  LendRequestType.lend: 'LEND',
  LendRequestType.$unknown: r'$unknown',
};

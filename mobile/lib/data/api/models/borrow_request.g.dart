// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'borrow_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BorrowRequest _$BorrowRequestFromJson(Map<String, dynamic> json) =>
    BorrowRequest(
      amountInCents: (json['amountInCents'] as num).toInt(),
      date: json['date'] as String,
      description: json['description'] as String,
      id: json['id'] as String,
      lender: json['lender'] as String,
      receivedInto: json['receivedInto'] as String,
      type: BorrowRequestType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$BorrowRequestToJson(BorrowRequest instance) =>
    <String, dynamic>{
      'amountInCents': instance.amountInCents,
      'date': instance.date,
      'description': instance.description,
      'id': instance.id,
      'lender': instance.lender,
      'receivedInto': instance.receivedInto,
      'type': _$BorrowRequestTypeEnumMap[instance.type]!,
    };

const _$BorrowRequestTypeEnumMap = {
  BorrowRequestType.borrow: 'BORROW',
  BorrowRequestType.$unknown: r'$unknown',
};

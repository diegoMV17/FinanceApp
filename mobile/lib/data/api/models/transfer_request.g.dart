// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'transfer_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TransferRequest _$TransferRequestFromJson(Map<String, dynamic> json) =>
    TransferRequest(
      amountInCents: (json['amountInCents'] as num).toInt(),
      date: json['date'] as String,
      description: json['description'] as String,
      from: json['from'] as String,
      id: json['id'] as String,
      to: json['to'] as String,
      type: TransferRequestType.fromJson(json['type'] as String),
    );

Map<String, dynamic> _$TransferRequestToJson(TransferRequest instance) =>
    <String, dynamic>{
      'amountInCents': instance.amountInCents,
      'date': instance.date,
      'description': instance.description,
      'from': instance.from,
      'id': instance.id,
      'to': instance.to,
      'type': _$TransferRequestTypeEnumMap[instance.type]!,
    };

const _$TransferRequestTypeEnumMap = {
  TransferRequestType.transfer: 'TRANSFER',
  TransferRequestType.$unknown: r'$unknown',
};

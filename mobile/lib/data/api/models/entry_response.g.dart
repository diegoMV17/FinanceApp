// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'entry_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EntryResponse _$EntryResponseFromJson(Map<String, dynamic> json) =>
    EntryResponse(
      account: json['account'] as String,
      accountName: json['accountName'] as String,
      amountInCents: (json['amountInCents'] as num).toInt(),
    );

Map<String, dynamic> _$EntryResponseToJson(EntryResponse instance) =>
    <String, dynamic>{
      'account': instance.account,
      'accountName': instance.accountName,
      'amountInCents': instance.amountInCents,
    };

// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'entry_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EntryResponse _$EntryResponseFromJson(Map<String, dynamic> json) =>
    EntryResponse(
      amountInCents: (json['amountInCents'] as num).toInt(),
      account: json['account'] as String?,
      accountName: json['accountName'] as String?,
    );

Map<String, dynamic> _$EntryResponseToJson(EntryResponse instance) =>
    <String, dynamic>{
      'account': instance.account,
      'accountName': instance.accountName,
      'amountInCents': instance.amountInCents,
    };

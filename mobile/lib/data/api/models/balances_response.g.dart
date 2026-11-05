// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'balances_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BalancesResponse _$BalancesResponseFromJson(Map<String, dynamic> json) =>
    BalancesResponse(
      accounts: (json['accounts'] as List<dynamic>?)
          ?.map((e) => BalanceResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
      asOf: json['asOf'] as String?,
    );

Map<String, dynamic> _$BalancesResponseToJson(BalancesResponse instance) =>
    <String, dynamic>{'accounts': instance.accounts, 'asOf': instance.asOf};

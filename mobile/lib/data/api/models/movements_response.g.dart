// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'movements_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MovementsResponse _$MovementsResponseFromJson(Map<String, dynamic> json) =>
    MovementsResponse(
      page: (json['page'] as num).toInt(),
      size: (json['size'] as num).toInt(),
      total: (json['total'] as num).toInt(),
      account: json['account'] as String?,
      movements: (json['movements'] as List<dynamic>?)
          ?.map((e) => MovementLineResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$MovementsResponseToJson(MovementsResponse instance) =>
    <String, dynamic>{
      'account': instance.account,
      'movements': instance.movements,
      'page': instance.page,
      'size': instance.size,
      'total': instance.total,
    };

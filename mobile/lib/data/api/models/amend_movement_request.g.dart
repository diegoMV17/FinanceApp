// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'amend_movement_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AmendMovementRequest _$AmendMovementRequestFromJson(
  Map<String, dynamic> json,
) => AmendMovementRequest(
  correction: MovementRequest.fromJson(
    json['correction'] as Map<String, dynamic>,
  ),
);

Map<String, dynamic> _$AmendMovementRequestToJson(
  AmendMovementRequest instance,
) => <String, dynamic>{'correction': instance.correction};

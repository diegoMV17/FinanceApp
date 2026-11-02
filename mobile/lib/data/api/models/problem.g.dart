// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'problem.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Problem _$ProblemFromJson(Map<String, dynamic> json) => Problem(
  detail: json['detail'] as String,
  status: (json['status'] as num).toInt(),
  title: json['title'] as String,
  type: ProblemType.fromJson(json['type'] as String),
  errorId: json['errorId'] as String?,
);

Map<String, dynamic> _$ProblemToJson(Problem instance) => <String, dynamic>{
  'detail': instance.detail,
  'errorId': instance.errorId,
  'status': instance.status,
  'title': instance.title,
  'type': _$ProblemTypeEnumMap[instance.type]!,
};

const _$ProblemTypeEnumMap = {
  ProblemType.undefined0: 'urn:finance:error:unauthorized',
  ProblemType.undefined1: 'urn:finance:error:forbidden',
  ProblemType.undefined2: 'urn:finance:error:too-many-attempts',
  ProblemType.undefined3: 'urn:finance:error:malformed-request',
  ProblemType.undefined4: 'urn:finance:error:invalid-request',
  ProblemType.undefined5: 'urn:finance:error:invalid-path',
  ProblemType.undefined6: 'urn:finance:error:rejected-request',
  ProblemType.undefined7: 'urn:finance:error:unsafe-text',
  ProblemType.undefined8: 'urn:finance:error:range-too-wide',
  ProblemType.undefined9: 'urn:finance:error:unsupported-media-type',
  ProblemType.undefined10: 'urn:finance:error:method-not-allowed',
  ProblemType.undefined11: 'urn:finance:error:not-found',
  ProblemType.undefined12: 'urn:finance:error:content-too-large',
  ProblemType.undefined13: 'urn:finance:error:duplicate-id',
  ProblemType.undefined14: 'urn:finance:error:duplicate-account-name',
  ProblemType.undefined15: 'urn:finance:error:duplicate-person-name',
  ProblemType.undefined16: 'urn:finance:error:already-cancelled',
  ProblemType.undefined17: 'urn:finance:error:already-archived',
  ProblemType.undefined18: 'urn:finance:error:account-still-holds-money',
  ProblemType.undefined19: 'urn:finance:error:person-already-archived',
  ProblemType.undefined20: 'urn:finance:error:person-still-has-open-accounts',
  ProblemType.undefined21: 'urn:finance:error:budget-already-superseded',
  ProblemType.undefined22: 'urn:finance:error:unexpected-account-type',
  ProblemType.undefined23: 'urn:finance:error:same-account-transfer',
  ProblemType.undefined24: 'urn:finance:error:account-needs-name',
  ProblemType.undefined25: 'urn:finance:error:person-needs-name',
  ProblemType.undefined26:
      'urn:finance:error:account-cannot-belong-to-a-person',
  ProblemType.undefined27: 'urn:finance:error:archived-person',
  ProblemType.undefined28: 'urn:finance:error:archived-account',
  ProblemType.undefined29: 'urn:finance:error:date-out-of-range',
  ProblemType.undefined30: 'urn:finance:error:reversed-range',
  ProblemType.undefined31: 'urn:finance:error:rule-violated',
  ProblemType.undefined32: 'urn:finance:error:book-busy',
  ProblemType.undefined33: 'urn:finance:error:internal-error',
  ProblemType.$unknown: r'$unknown',
};

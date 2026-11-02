// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:json_annotation/json_annotation.dart';

import 'problem_type.dart';

part 'problem.g.dart';

/// Un error de la API (RFC 9457). El detail es un texto fijo por type: nunca trae datos del libro.
@JsonSerializable()
class Problem {
  const Problem({
    required this.detail,
    required this.status,
    required this.title,
    required this.type,
    this.errorId,
  });

  factory Problem.fromJson(Map<String, Object?> json) =>
      _$ProblemFromJson(json);

  final String detail;

  /// Solo en un error inesperado (500): con qué preguntar en el log del servidor.
  final String? errorId;
  final int status;
  final String title;
  final ProblemType type;

  Map<String, Object?> toJson() => _$ProblemToJson(this);
}

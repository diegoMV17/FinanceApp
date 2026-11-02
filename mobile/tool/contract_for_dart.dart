// El contrato, ajustado para el generador de Dart. Lo corre
// tool/generate_api.sh antes de swagger_parser; nunca se comitea su salida.
//
// Uso: dart run tool/contract_for_dart.dart <contrato> <salida>
//
// El único ajuste: las fechas de calendario. swagger_parser convierte
// `format: date` y `format: date-time` por igual en DateTime, y un DateTime se
// serializa como instante ("2026-11-02T00:00:00.000"): el servidor espera
// "2026-11-02" y respondería 400. Una fecha de calendario no es un instante, así
// que aquí deja de tener formato y se genera como String. Los instantes
// (`date-time`, como archivedAt) siguen siendo DateTime.
//
// El ajuste vive del lado de mobile porque el problema es del generador: el
// contrato dice la verdad sobre la API, y la API no cambia para complacerlo.
import 'dart:convert';
import 'dart:io';

/// Una copia del documento sin `format: date` en ningún esquema.
Object? calendarDatesAsText(Object? node) {
  if (node is Map<String, Object?>) {
    final bool isCalendarDate =
        node['type'] == 'string' && node['format'] == 'date';
    return <String, Object?>{
      for (final MapEntry<String, Object?> entry in node.entries)
        if (!(isCalendarDate && entry.key == 'format'))
          entry.key: calendarDatesAsText(entry.value),
    };
  }
  if (node is List<Object?>) {
    return <Object?>[
      for (final Object? item in node) calendarDatesAsText(item),
    ];
  }
  return node;
}

void main(List<String> args) {
  if (args.length != 2) {
    stderr.writeln(
      'uso: dart run tool/contract_for_dart.dart <contrato> <salida>',
    );
    exit(64);
  }
  final Object? contract = jsonDecode(File(args[0]).readAsStringSync());
  File(args[1])
    ..createSync(recursive: true)
    ..writeAsStringSync(
      const JsonEncoder.withIndent('  ').convert(calendarDatesAsText(contract)),
    );
}

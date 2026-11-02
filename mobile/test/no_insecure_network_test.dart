import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Lo que nunca se escribe en `lib/`, también en lo generado.
///
/// Cada una de estas es una línea que alguien agrega «para depurar» y se
/// queda: un interceptor de logs imprime la cabecera con el token y los
/// cuerpos con montos y nombres; un `badCertificateCallback` que devuelve true
/// apaga la verificación de certificados para todo; un `print` en release va
/// al log del sistema, que otras herramientas leen.
void main() {
  final Map<String, RegExp> forbidden = <String, RegExp>{
    'interceptor de logs': RegExp(r'\bLogInterceptor\b'),
    'certificados sin verificar': RegExp(
      r'badCertificateCallback|validateCertificate|onHttpClientCreate',
    ),
    'print': RegExp(r'(^|[^\w.])print\('),
    'debugPrint': RegExp(r'\bdebugPrint\('),
  };

  final List<File> sources = <File>[
    for (final FileSystemEntity entity in Directory(
      'lib',
    ).listSync(recursive: true))
      if (entity is File && entity.path.endsWith('.dart')) entity,
  ];

  test('hay código que barrer', () {
    expect(sources, isNotEmpty);
  });

  for (final MapEntry<String, RegExp> rule in forbidden.entries) {
    test('ningún archivo de lib/ trae ${rule.key}', () {
      final List<String> offenders = <String>[
        for (final File file in sources)
          if (rule.value.hasMatch(file.readAsStringSync())) file.path,
      ];

      expect(offenders, isEmpty);
    });
  }
}

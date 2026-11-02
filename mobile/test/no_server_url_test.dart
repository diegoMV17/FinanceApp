import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// La dirección del servidor no va en el código ni en el contrato.
///
/// Es el nombre de la tailnet, y es privado: escrito en un repositorio público
/// le dice a cualquiera dónde vive la API. Se escribe a mano en la app la
/// primera vez (paso 7b), como el token. Este test barre todo `lib/` —también
/// lo generado— y el contrato, y no deja pasar ni una URL.
void main() {
  final RegExp anAddress = RegExp(r'https?://|\.ts\.net');

  test('ningún archivo de lib/ trae una dirección', () {
    final List<String> offenders = <String>[
      for (final FileSystemEntity file in Directory(
        'lib',
      ).listSync(recursive: true))
        if (file is File &&
            file.path.endsWith('.dart') &&
            anAddress.hasMatch(file.readAsStringSync()))
          file.path,
    ];

    expect(offenders, isEmpty);
  });

  test('el contrato tampoco', () {
    final String contract = File('../contract/openapi.json').readAsStringSync();

    expect(anAddress.hasMatch(contract), isFalse);
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/storage/server_settings.dart';

void main() {
  group('la dirección del servidor', () {
    test('https con host, sin ruta: se guarda como origen', () {
      final ServerAddress address = ServerAddress.parse(
        'https://finanzas.ejemplo.invalid',
      );

      expect(address.origin, 'https://finanzas.ejemplo.invalid');
    });

    test('la barra final y los espacios de los bordes no cuentan', () {
      expect(
        ServerAddress.parse('  https://finanzas.ejemplo.invalid/ ').origin,
        'https://finanzas.ejemplo.invalid',
      );
    });

    test('con puerto', () {
      expect(
        ServerAddress.parse('https://finanzas.ejemplo.invalid:8443').origin,
        'https://finanzas.ejemplo.invalid:8443',
      );
    });

    final Map<String, ServerAddressProblem> rejected =
        <String, ServerAddressProblem>{
          // Nunca HTTP en claro: el token viajaría legible.
          'http://finanzas.ejemplo.invalid': ServerAddressProblem.notHttps,
          'finanzas.ejemplo.invalid': ServerAddressProblem.notHttps,
          'ftp://finanzas.ejemplo.invalid': ServerAddressProblem.notHttps,
          'https://': ServerAddressProblem.noHost,
          // Unas credenciales en la URL acabarían en algún log.
          'https://yo:clave@finanzas.ejemplo.invalid':
              ServerAddressProblem.hasCredentials,
          'https://finanzas.ejemplo.invalid/api': ServerAddressProblem.hasPath,
          'https://finanzas.ejemplo.invalid/?x=1':
              ServerAddressProblem.hasQueryOrFragment,
          'https://finanzas.ejemplo.invalid/#x':
              ServerAddressProblem.hasQueryOrFragment,
          '': ServerAddressProblem.empty,
          '   ': ServerAddressProblem.empty,
          'https://exa mple.invalid': ServerAddressProblem.malformed,
        };
    for (final MapEntry<String, ServerAddressProblem> entry
        in rejected.entries) {
      test('"${entry.key}" no es una dirección: ${entry.value.name}', () {
        expect(
          () => ServerAddress.parse(entry.key),
          throwsA(
            isA<InvalidServerAddress>().having(
              (InvalidServerAddress e) => e.problem,
              'problem',
              entry.value,
            ),
          ),
        );
      });
    }
  });

  group('el token', () {
    // 64 hexadecimales: lo que genera deploy/generate-secrets.sh.
    const String valid =
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';

    test('uno de verdad se acepta tal cual', () {
      expect(ApiToken(valid).value, valid);
    });

    test('menos de 32 caracteres no es un token: el servidor no lo admite', () {
      expect(
        () => ApiToken('a' * 31),
        throwsA(
          isA<InvalidApiToken>().having(
            (InvalidApiToken e) => e.problem,
            'problem',
            ApiTokenProblem.tooShort,
          ),
        ),
      );
      expect(ApiToken('a' * 32).value, hasLength(32));
    });

    // Se rechaza y no se recorta, igual que en el servidor: recortar dejaría
    // guardado un token distinto del que se escribió.
    final Map<String, String> bad = <String, String>{
      'salto de línea al final': '$valid\n',
      'espacio delante': ' $valid',
      'espacio en medio': '${valid.substring(0, 40)} ${valid.substring(40)}',
      'carácter invisible': '$valid​',
      'fuera de ASCII': '$validñ',
    };
    for (final MapEntry<String, String> entry in bad.entries) {
      test('${entry.key}: no se recorta, se rechaza', () {
        expect(
          () => ApiToken(entry.value),
          throwsA(
            isA<InvalidApiToken>().having(
              (InvalidApiToken e) => e.problem,
              'problem',
              ApiTokenProblem.invalidCharacters,
            ),
          ),
        );
      });
    }

    test('ni el token ni la configuración lo imprimen', () {
      final ApiToken token = ApiToken(valid);
      final ServerSettings settings = ServerSettings(
        address: ServerAddress.parse('https://finanzas.ejemplo.invalid'),
        token: token,
      );

      expect(token.toString(), isNot(contains(valid)));
      expect(settings.toString(), isNot(contains(valid)));
      expect(settings.toString(), contains('finanzas.ejemplo.invalid'));
    });

    test('ni el error dice qué se escribió', () {
      final String written = '${valid.substring(0, 40)} secreto';
      expect(
        () => ApiToken(written),
        throwsA(
          isA<InvalidApiToken>().having(
            (InvalidApiToken e) => e.toString(),
            'toString',
            isNot(contains(valid.substring(0, 40))),
          ),
        ),
      );
    });
  });
}

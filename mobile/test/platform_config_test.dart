import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Lo que la seguridad de la app pide a los archivos de cada plataforma.
///
/// Son líneas de configuración que nadie mira hasta que algo sale mal, y que
/// una plantilla nueva o un «arreglo rápido» pueden volver a dejar como
/// estaban. Leerlas en un test es la forma de que no dependan de la memoria
/// de nadie.
void main() {
  final String manifest = File('android/app/src/main/AndroidManifest.xml')
      .readAsStringSync();

  group('Android: lo que el respaldo del sistema no se lleva', () {
    // El respaldo de Android copiaría la base y lo guardado por la app a la
    // nube o a otro teléfono: lo que el almacenamiento seguro y la base
    // cifrada protegen.
    test('allowBackup y fullBackupContent en false', () {
      expect(manifest, contains('android:allowBackup="false"'));
      expect(manifest, contains('android:fullBackupContent="false"'));
    });

    test('y en Android 12+, también fuera de la transferencia entre '
        'teléfonos, que allowBackup solo no apaga', () {
      expect(
        manifest,
        contains('android:dataExtractionRules="@xml/data_extraction_rules"'),
      );
      final String rules = File(
        'android/app/src/main/res/xml/data_extraction_rules.xml',
      ).readAsStringSync();
      for (final String section in <String>[
        'cloud-backup',
        'device-transfer',
      ]) {
        final RegExp block = RegExp('<$section>([\\s\\S]*?)</$section>');
        final String? excluded = block.firstMatch(rules)?.group(1);
        expect(excluded, isNotNull, reason: section);
        for (final String domain in <String>[
          'root',
          'file',
          'database',
          'sharedpref',
          'external',
        ]) {
          expect(
            excluded,
            contains('<exclude domain="$domain" path="."/>'),
            reason: '$section / $domain',
          );
        }
        expect(excluded, isNot(contains('<include')), reason: section);
      }
    });
  });

  test('Android: nada de HTTP en claro', () {
    expect(manifest, contains('android:usesCleartextTraffic="false"'));
  });

  test('Android: la app sale tapada en recientes y sin capturas', () {
    final String activity = File(
      'android/app/src/main/kotlin/com/personalfinance/mobile/MainActivity.kt',
    ).readAsStringSync();

    expect(activity, contains('FLAG_SECURE'));
    expect(activity, contains('FlutterFragmentActivity'));
  });

  group('Android: la firma de release', () {
    final String gradle = File('android/app/build.gradle.kts')
        .readAsStringSync();

    test('nunca con la llave de depuración', () {
      // Quien tenga una llave puede publicar una actualización que el
      // teléfono acepta como de esta app. La de depuración la tiene cualquier
      // máquina con el SDK.
      expect(gradle, isNot(contains('signingConfigs.getByName("debug")')));
    });

    test('la llave sale de key.properties, que no se versiona', () {
      expect(gradle, contains('key.properties'));
      expect(gradle, contains('signingConfigs.getByName("release")'));
      final ProcessResult ignored = Process.runSync('git', <String>[
        'check-ignore',
        '-q',
        'android/key.properties',
      ]);
      expect(ignored.exitCode, 0, reason: 'key.properties tiene que ignorarse');
    });
  });

  test('iOS: dice para qué usa Face ID (sin esto, el sistema la cierra)', () {
    final String plist = File('ios/Runner/Info.plist').readAsStringSync();

    expect(plist, contains('<key>NSFaceIDUsageDescription</key>'));
  });
}

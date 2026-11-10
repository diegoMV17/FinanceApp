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

  test('Android: release también tiene red', () {
    // La plantilla de Flutter lo declara solo en src/debug y src/profile, y
    // ningún plugin lo trae. Sin esto, todo funciona hasta el primer APK
    // firmado, que siempre diría que no llega al servidor.
    expect(
      manifest,
      contains('<uses-permission android:name="android.permission.INTERNET"/>'),
    );
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

  group('Android: el ícono y el arranque son los de la app', () {
    const String res = 'android/app/src/main/res';
    String read(String path) => File('$res/$path').readAsStringSync();

    test('el ícono es adaptativo: el vector sobre el verde, y monocromo', () {
      expect(manifest, contains('android:icon="@mipmap/ic_launcher"'));
      final String adaptive = read('mipmap-anydpi-v26/ic_launcher.xml');
      expect(adaptive, contains('<adaptive-icon'));
      // Cada capa por su nombre: el monocromo usa el mismo dibujo, así que
      // buscar solo el atributo no vería que falta el primer plano.
      for (final String layer in <String>[
        '<background android:drawable="@color/ic_launcher_background"',
        '<foreground android:drawable="@drawable/ic_launcher_foreground"',
        '<monochrome android:drawable="@drawable/ic_launcher_foreground"',
      ]) {
        expect(adaptive, contains(layer), reason: layer);
      }
      expect(read('drawable/ic_launcher_foreground.xml'), contains('<vector'));
      expect(read('values/colors.xml'), contains('#FF1B6B50'));
    });

    test('Android 7 tiene su PNG en cada densidad, y no es el de Flutter', () {
      for (final String density in <String>[
        'mdpi',
        'hdpi',
        'xhdpi',
        'xxhdpi',
        'xxxhdpi',
      ]) {
        final List<int> png = File('$res/mipmap-$density/ic_launcher.png')
            .readAsBytesSync();
        expect(png.sublist(1, 4), 'PNG'.codeUnits, reason: density);
        // El de la plantilla de Flutter es el logo azul; el nuestro lleva
        // un comentario de texto con su origen.
        expect(
          String.fromCharCodes(png),
          contains('FinanceApp'),
          reason: density,
        );
      }
    });

    test('el arranque: el color de la app con el ícono, en claro y oscuro', () {
      for (final String path in <String>[
        'drawable/launch_background.xml',
        'drawable-v21/launch_background.xml',
      ]) {
        final String background = read(path);
        expect(background, contains('@color/splash_background'), reason: path);
        expect(
          background,
          contains('@drawable/ic_launcher_foreground'),
          reason: path,
        );
      }
      expect(read('values/colors.xml'), contains('name="splash_background"'));
      expect(
        read('values-night/colors.xml'),
        contains('name="splash_background"'),
      );
    });

    test('Android 12+ usa su propio arranque: también configurado', () {
      for (final String path in <String>[
        'values-v31/styles.xml',
        'values-night-v31/styles.xml',
      ]) {
        final String styles = read(path);
        expect(styles, contains('name="LaunchTheme"'), reason: path);
        expect(
          styles,
          contains('android:windowSplashScreenBackground'),
          reason: path,
        );
        expect(
          styles,
          contains('android:windowSplashScreenAnimatedIcon'),
          reason: path,
        );
      }
    });
  });

  group('la fuente va dentro de la app, nunca se descarga', () {
    final String pubspec = File('pubspec.yaml').readAsStringSync();

    test('Inter, declarada con sus cuatro pesos', () {
      expect(pubspec, contains('family: Inter'));
      for (final (String file, int weight) in <(String, int)>[
        ('Inter-Regular.ttf', 400),
        ('Inter-Medium.ttf', 500),
        ('Inter-SemiBold.ttf', 600),
        ('Inter-Bold.ttf', 700),
      ]) {
        expect(pubspec, contains('assets/fonts/$file'), reason: file);
        expect(File('assets/fonts/$file').existsSync(), isTrue, reason: file);
        expect(pubspec, contains('weight: $weight'), reason: file);
      }
    });

    test('con su licencia OFL junto', () {
      expect(
        File('assets/fonts/Inter-LICENSE.txt').readAsStringSync(),
        contains('SIL Open Font License, Version 1.1'),
      );
      expect(pubspec, contains('assets/fonts/Inter-LICENSE.txt'));
    });

    test('sin google_fonts, que baja las fuentes de internet: ni directa '
        'ni traída por otro paquete', () {
      expect(
        File('pubspec.lock').readAsStringSync(),
        isNot(contains('google_fonts')),
      );
    });
  });

  test('iOS: dice para qué usa Face ID (sin esto, el sistema la cierra)', () {
    final String plist = File('ios/Runner/Info.plist').readAsStringSync();

    expect(plist, contains('<key>NSFaceIDUsageDescription</key>'));
  });
}

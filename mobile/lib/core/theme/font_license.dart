import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// La licencia de Inter (SIL OFL 1.1) pide ir con la fuente: queda en la
/// pantalla de licencias de la app, junto a la de los paquetes.
void registerFontLicenses() {
  LicenseRegistry.addLicense(() async* {
    final String text = await rootBundle.loadString(
      'assets/fonts/Inter-LICENSE.txt',
    );
    yield LicenseEntryWithLineBreaks(<String>['Inter'], text);
  });
}

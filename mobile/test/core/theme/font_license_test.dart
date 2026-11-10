import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/theme/font_license.dart';

/// La licencia OFL de Inter pide ir con la fuente: aparece en «Licencias».
void main() {
  test('la licencia de Inter queda registrada', () async {
    TestWidgetsFlutterBinding.ensureInitialized();
    registerFontLicenses();

    final List<LicenseEntry> entries = await LicenseRegistry.licenses.toList();
    final LicenseEntry inter = entries.firstWhere(
      (LicenseEntry e) => e.packages.contains('Inter'),
    );

    expect(
      inter.paragraphs.map((LicenseParagraph p) => p.text).join('\n'),
      contains('SIL Open Font License'),
    );
  });
}

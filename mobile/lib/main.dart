import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/theme/font_license.dart';

void main() {
  registerFontLicenses();
  // El ProviderScope envuelve todo: los view models viven en él.
  runApp(const ProviderScope(child: FinanceApp()));
}

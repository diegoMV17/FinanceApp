import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';

void main() {
  // El ProviderScope envuelve todo: los view models viven en él.
  runApp(const ProviderScope(child: FinanceApp()));
}

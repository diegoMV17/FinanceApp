import 'package:flutter_riverpod/flutter_riverpod.dart';

/// La hora del teléfono, en su zona. Un proveedor para que los tests fijen
/// el día sin depender de cuándo corren.
final Provider<DateTime Function()> clockProvider =
    Provider<DateTime Function()>((Ref ref) => DateTime.now);

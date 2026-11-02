/// Las reglas de dependencia entre carpetas de `lib/`, como funciones puras
/// sobre rutas e imports: así se prueban con archivos inventados sin tener que
/// escribir código que viole la arquitectura para ver fallar el test.
///
/// Las rutas y los imports son relativos a `lib/`
/// (`features/people/ui/people_screen.dart`). Un import a un paquete
/// (`package:flutter/...`, `dart:async`) se deja pasar: las reglas hablan de
/// este proyecto.
library;

import 'dart:io';

enum Rule {
  /// Una funcionalidad no importa el interior de otra; lo compartido sube a
  /// `core/` o a `data/`.
  featureReachesIntoAnother,

  /// Un widget o su view model no llaman a la API ni a la base: le piden al
  /// repositorio de su funcionalidad.
  uiTalksToData,

  /// El cliente generado solo se ve envuelto, desde `core/network` o `data/`.
  generatedClientLeaks,

  /// `core/` es lo común: no conoce ninguna funcionalidad.
  coreKnowsItsCallers,
}

class DartFile {
  const DartFile(this.path, this.imports);

  /// Ruta relativa a `lib/`, con `/` como separador.
  final String path;

  /// Lo que importa, ya resuelto a rutas relativas a `lib/`. Los imports de
  /// paquetes quedan tal como están.
  final List<String> imports;
}

class Violation {
  const Violation(this.rule, this.from, this.to);

  final Rule rule;
  final String from;
  final String to;

  @override
  String toString() => '${rule.name}: $from -> $to';
}

const String _features = 'features/';
const String _core = 'core/';
const String _coreNetwork = 'core/network/';
const String _data = 'data/';
const String _dataApi = 'data/api/';

List<Violation> violations(Iterable<DartFile> files) {
  final List<Violation> found = <Violation>[];
  for (final DartFile file in files) {
    for (final String target in file.imports) {
      if (_isPackageImport(target)) {
        continue;
      }
      final Rule? broken = _brokenRule(file.path, target);
      if (broken != null) {
        found.add(Violation(broken, file.path, target));
      }
    }
  }
  return found;
}

Rule? _brokenRule(String from, String to) {
  final String? fromFeature = _featureOf(from);
  final String? toFeature = _featureOf(to);

  if (fromFeature != null && toFeature != null && fromFeature != toFeature) {
    return Rule.featureReachesIntoAnother;
  }
  if (_isFeatureUi(from) && to.startsWith(_data)) {
    return Rule.uiTalksToData;
  }
  if (to.startsWith(_dataApi) &&
      !from.startsWith(_coreNetwork) &&
      !from.startsWith(_data)) {
    return Rule.generatedClientLeaks;
  }
  if (from.startsWith(_core) && !from.startsWith(_coreNetwork)) {
    if (to.startsWith(_features) || to.startsWith(_data)) {
      return Rule.coreKnowsItsCallers;
    }
  }
  return null;
}

bool _isPackageImport(String target) =>
    target.startsWith('dart:') || target.startsWith('package:');

/// `features/people/ui/x.dart` -> `people`; cualquier otra cosa, `null`.
String? _featureOf(String path) {
  if (!path.startsWith(_features)) {
    return null;
  }
  final List<String> parts = path.split('/');
  return parts.length > 1 ? parts[1] : null;
}

bool _isFeatureUi(String path) =>
    _featureOf(path) != null &&
    path.split('/').length > 2 &&
    path.split('/')[2] == 'ui';

/// Lee `lib/` y devuelve cada archivo Dart con sus imports ya resueltos.
///
/// `package:mobile/app.dart` y `../app.dart` son la misma dependencia: si solo
/// se mirara el prefijo `package:`, un import relativo saltaría todas las
/// reglas.
List<DartFile> libraryFiles([String root = 'lib']) {
  final Directory lib = Directory(root);
  if (!lib.existsSync()) {
    return const <DartFile>[];
  }
  final List<DartFile> files = <DartFile>[];
  for (final FileSystemEntity entity in lib.listSync(recursive: true)) {
    if (entity is! File || !entity.path.endsWith('.dart')) {
      continue;
    }
    final String path = _asLibraryPath(entity.path, root);
    files.add(DartFile(path, _importsOf(entity.readAsStringSync(), path)));
  }
  return files;
}

String _asLibraryPath(String filePath, String root) {
  final String normalized = filePath.replaceAll(r'\', '/');
  final int cut = normalized.indexOf('$root/');
  return cut < 0 ? normalized : normalized.substring(cut + root.length + 1);
}

final RegExp _directive = RegExp(
  r'''^\s*(?:import|export)\s+['"]([^'"]+)['"]''',
  multiLine: true,
);

const String _ownPackage = 'package:mobile/';

List<String> _importsOf(String source, String fromPath) {
  return _directive
      .allMatches(source)
      .map((RegExpMatch match) => match.group(1)!)
      .map((String target) => _resolve(target, fromPath))
      .toList();
}

String _resolve(String target, String fromPath) {
  if (target.startsWith(_ownPackage)) {
    return target.substring(_ownPackage.length);
  }
  if (_isPackageImport(target)) {
    return target;
  }
  final List<String> parts = fromPath.split('/')..removeLast();
  for (final String segment in target.split('/')) {
    if (segment == '.' || segment.isEmpty) {
      continue;
    }
    if (segment == '..') {
      if (parts.isNotEmpty) {
        parts.removeLast();
      }
      continue;
    }
    parts.add(segment);
  }
  return parts.join('/');
}

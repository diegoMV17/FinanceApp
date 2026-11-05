import 'dart:convert';
import 'dart:io';

/// Cuerpos de movimiento sacados del contrato y no de una lista escrita en un
/// test: si el servidor agrega un tipo, quien los recorre lo ve solo.
Map<String, Object?> contractSchemas() {
  final Map<String, Object?> contract = jsonDecode(
    File('../contract/openapi.json').readAsStringSync(),
  ) as Map<String, Object?>;
  final Map<String, Object?> components =
      contract['components']! as Map<String, Object?>;
  return components['schemas']! as Map<String, Object?>;
}

/// El `type` de cada movimiento y el esquema que le corresponde.
Map<String, Object?> movementTypes(Map<String, Object?> schemas) {
  final Map<String, Object?> movement =
      schemas['MovementRequest']! as Map<String, Object?>;
  final Map<String, Object?> discriminator =
      movement['discriminator']! as Map<String, Object?>;
  return discriminator['mapping']! as Map<String, Object?>;
}

/// El UUID v4 con el que salen las muestras.
const String sampleId = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';

/// Un cuerpo válido de una variante: su type y un valor de la forma de cada
/// campo obligatorio.
Map<String, Object?> sampleOf(Map<String, Object?> schemas, String name) {
  final Map<String, Object?> schema = schemas[name]! as Map<String, Object?>;
  final Map<String, Object?> properties =
      schema['properties']! as Map<String, Object?>;
  final List<Object?> required = schema['required']! as List<Object?>;
  return <String, Object?>{
    for (final Object? field in required)
      field! as String: _sampleValue(
        properties[field]! as Map<String, Object?>,
      ),
  };
}

Object _sampleValue(Map<String, Object?> property) {
  final List<Object?>? values = property['enum'] as List<Object?>?;
  if (values != null) {
    return values.single!;
  }
  return switch ((property['type'], property['format'])) {
    ('integer', _) => 1234,
    ('string', 'uuid') => sampleId,
    ('string', 'date') => '2026-11-02',
    ('string', _) => 'Prueba',
    _ => throw StateError('campo sin muestra: $property'),
  };
}

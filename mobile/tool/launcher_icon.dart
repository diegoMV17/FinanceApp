// Los PNG del ícono para Android 7 (minSdk 24), que no tiene ícono adaptativo.
//
// Salen de la misma geometría que el vector
// android/app/src/main/res/drawable/ic_launcher_foreground.xml: tres barras
// blancas sobre un cuadrado verde de esquinas redondeadas. Puro Dart (zlib de
// dart:io y un CRC32 a mano): ninguna herramienta ni paquete más.
//
// Se corre desde mobile/ cuando cambia el dibujo, y se comitea lo que salga:
//   dart run tool/launcher_icon.dart
import 'dart:io';
import 'dart:typed_data';

/// El lienzo del vector es de 108; el ícono de Android 7 muestra la parte
/// central de 72 (de 18 a 90), donde está el dibujo.
const double _from = 18;
const double _span = 72;

/// Las barras del vector: izquierda, arriba, derecha, abajo; radio 3.
const List<List<double>> _bars = <List<double>>[
  <double>[34, 56, 46, 74],
  <double>[48, 46, 60, 74],
  <double>[62, 34, 74, 74],
];
const double _barRadius = 3;

/// El cuadrado de fondo: radio del 22 % del lado, como los íconos del sistema.
const double _backgroundRadius = 0.22;

const List<int> _green = <int>[0x1B, 0x6B, 0x50];
const int _samples = 4;

const Map<String, int> _densities = <String, int>{
  'mdpi': 48,
  'hdpi': 72,
  'xhdpi': 96,
  'xxhdpi': 144,
  'xxxhdpi': 192,
};

void main() {
  for (final MapEntry<String, int> density in _densities.entries) {
    final File out = File(
      'android/app/src/main/res/mipmap-${density.key}/ic_launcher.png',
    );
    out.writeAsBytesSync(_png(density.value));
    stdout.writeln('${out.path}: ${density.value} px');
  }
}

Uint8List _png(int size) {
  final BytesBuilder rows = BytesBuilder();
  for (int y = 0; y < size; y++) {
    rows.addByte(0); // Sin filtro.
    for (int x = 0; x < size; x++) {
      rows.add(_pixel(x, y, size));
    }
  }
  final BytesBuilder png = BytesBuilder()
    ..add(<int>[0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    ..add(
      _chunk(
        'IHDR',
        (ByteData(13)
              ..setUint32(0, size)
              ..setUint32(4, size)
              ..setUint8(8, 8) // Bits por canal.
              ..setUint8(9, 6)) // RGBA.
            .buffer
            .asUint8List(),
      ),
    )
    ..add(
      _chunk(
        'tEXt',
        Uint8List.fromList(
          'Software\u0000FinanceApp tool/launcher_icon.dart'.codeUnits,
        ),
      ),
    )
    ..add(
      _chunk(
        'IDAT',
        Uint8List.fromList(ZLibCodec(level: 9).encode(rows.takeBytes())),
      ),
    )
    ..add(_chunk('IEND', Uint8List(0)));
  return png.takeBytes();
}

/// Cuánto de cada pixel cae en el fondo y en las barras, con 4×4 muestras.
List<int> _pixel(int x, int y, int size) {
  int background = 0;
  int bar = 0;
  for (int sy = 0; sy < _samples; sy++) {
    for (int sx = 0; sx < _samples; sx++) {
      final double u = (x + (sx + 0.5) / _samples) / size;
      final double v = (y + (sy + 0.5) / _samples) / size;
      if (!_inRoundedRect(u, v, 0, 0, 1, 1, _backgroundRadius)) {
        continue;
      }
      background++;
      final double vx = _from + u * _span;
      final double vy = _from + v * _span;
      if (_bars.any(
        (List<double> b) =>
            _inRoundedRect(vx, vy, b[0], b[1], b[2], b[3], _barRadius),
      )) {
        bar++;
      }
    }
  }
  const int total = _samples * _samples;
  if (background == 0) {
    return const <int>[0, 0, 0, 0];
  }
  // El blanco de las barras sobre el verde, y el borde del fondo suavizado.
  final double white = bar / background;
  return <int>[
    for (final int channel in _green)
      (channel + (255 - channel) * white).round(),
    (255 * background / total).round(),
  ];
}

bool _inRoundedRect(
  double x,
  double y,
  double left,
  double top,
  double right,
  double bottom,
  double radius,
) {
  if (x < left || x > right || y < top || y > bottom) {
    return false;
  }
  final double cx = x < left + radius
      ? left + radius
      : (x > right - radius ? right - radius : x);
  final double cy = y < top + radius
      ? top + radius
      : (y > bottom - radius ? bottom - radius : y);
  final double dx = x - cx;
  final double dy = y - cy;
  return dx * dx + dy * dy <= radius * radius;
}

Uint8List _chunk(String type, Uint8List data) {
  final Uint8List typed = Uint8List.fromList(type.codeUnits);
  final ByteData length = ByteData(4)..setUint32(0, data.length);
  final ByteData crc = ByteData(4)
    ..setUint32(0, _crc32(<int>[...typed, ...data]));
  return (BytesBuilder()
        ..add(length.buffer.asUint8List())
        ..add(typed)
        ..add(data)
        ..add(crc.buffer.asUint8List()))
      .takeBytes();
}

final List<int> _crcTable = List<int>.generate(256, (int n) {
  int c = n;
  for (int k = 0; k < 8; k++) {
    c = (c & 1) != 0 ? 0xEDB88320 ^ (c >> 1) : c >> 1;
  }
  return c;
});

int _crc32(List<int> bytes) {
  int crc = 0xFFFFFFFF;
  for (final int byte in bytes) {
    crc = _crcTable[(crc ^ byte) & 0xFF] ^ (crc >> 8);
  }
  return crc ^ 0xFFFFFFFF;
}

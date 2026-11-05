/// A qué servidor habla la app y con qué token.
///
/// Los dos se escriben a mano en la app la primera vez y viven solo en el
/// almacenamiento seguro: la dirección es el nombre de la tailnet, que es
/// privado, y el token abre el libro entero. Ninguno de los dos va en el
/// código ni en el repositorio.
library;

/// Por qué un texto no es la dirección del servidor.
enum ServerAddressProblem {
  empty,
  malformed,

  /// Solo https: por http el token viajaría legible.
  notHttps,
  noHost,

  /// `usuario:clave@` en la URL acabaría escrito en algún log.
  hasCredentials,

  /// La API vive en la raíz; una ruta es casi seguro un error de copia.
  hasPath,
  hasQueryOrFragment,
}

class InvalidServerAddress implements Exception {
  const InvalidServerAddress(this.problem);

  final ServerAddressProblem problem;

  @override
  String toString() => 'InvalidServerAddress(${problem.name})';
}

/// El origen del servidor: esquema https, host y, si hace falta, puerto. Nada
/// más.
///
/// Imposible de construir inválida: el único camino es [ServerAddress.parse].
final class ServerAddress {
  ServerAddress._(this.origin);

  /// Lee lo que escribió el usuario. Los espacios de los bordes y una barra
  /// final no cuentan; todo lo demás que no sea un origen https se rechaza.
  factory ServerAddress.parse(String text) {
    final String trimmed = text.trim();
    if (trimmed.isEmpty) {
      throw const InvalidServerAddress(ServerAddressProblem.empty);
    }
    if (trimmed.contains(RegExp(r'\s'))) {
      throw const InvalidServerAddress(ServerAddressProblem.malformed);
    }
    final Uri? uri = Uri.tryParse(trimmed);
    if (uri == null) {
      throw const InvalidServerAddress(ServerAddressProblem.malformed);
    }
    if (uri.scheme != 'https') {
      throw const InvalidServerAddress(ServerAddressProblem.notHttps);
    }
    if (uri.host.isEmpty) {
      throw const InvalidServerAddress(ServerAddressProblem.noHost);
    }
    if (uri.userInfo.isNotEmpty) {
      throw const InvalidServerAddress(ServerAddressProblem.hasCredentials);
    }
    if (uri.hasQuery || uri.hasFragment) {
      throw const InvalidServerAddress(ServerAddressProblem.hasQueryOrFragment);
    }
    if (uri.path.isNotEmpty && uri.path != '/') {
      throw const InvalidServerAddress(ServerAddressProblem.hasPath);
    }
    return ServerAddress._(uri.origin);
  }

  /// Esquema, host y puerto si lo hay, sin barra final.
  final String origin;

  @override
  bool operator ==(Object other) =>
      other is ServerAddress && other.origin == origin;

  @override
  int get hashCode => origin.hashCode;

  @override
  String toString() => origin;
}

enum ApiTokenProblem {
  /// El servidor no arranca con un token de menos de 32 bytes.
  tooShort,

  /// Espacios, saltos de línea, invisibles o algo fuera de ASCII.
  invalidCharacters,
}

/// Nunca dice qué se escribió: el texto rechazado puede ser casi el token.
class InvalidApiToken implements Exception {
  const InvalidApiToken(this.problem);

  final ApiTokenProblem problem;

  @override
  String toString() => 'InvalidApiToken(${problem.name})';
}

/// El token de la API.
///
/// El mismo criterio que `ApiTokenProperties` en el servidor: al menos 32
/// caracteres, solo ASCII imprimible, y lo que no cumple **se rechaza en vez
/// de recortarse**. Un token pegado con un salto de línea al final, recortado
/// en silencio, dejaría guardado algo distinto de lo que se escribió.
final class ApiToken {
  factory ApiToken(String value) {
    if (!_printableAscii.hasMatch(value)) {
      throw const InvalidApiToken(ApiTokenProblem.invalidCharacters);
    }
    if (value.length < minLength) {
      throw const InvalidApiToken(ApiTokenProblem.tooShort);
    }
    return ApiToken._(value);
  }

  ApiToken._(this.value);

  static const int minLength = 32;

  static final RegExp _printableAscii = RegExp(r'^[\x21-\x7E]*$');

  /// Para la cabecera `Authorization` y el almacenamiento seguro. Para nada
  /// más.
  final String value;

  @override
  String toString() => 'ApiToken(oculto)';
}

/// La dirección y el token juntos: o están los dos, o no hay configuración.
final class ServerSettings {
  const ServerSettings({required this.address, required this.token});

  final ServerAddress address;
  final ApiToken token;

  @override
  String toString() => 'ServerSettings(${address.origin})';
}

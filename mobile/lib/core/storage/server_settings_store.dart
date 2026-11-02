import 'secure_store.dart';
import 'server_settings.dart';

/// Guarda y lee la dirección del servidor y el token, en el almacenamiento
/// seguro y en ningún otro sitio.
class ServerSettingsStore {
  const ServerSettingsStore(this._secure);

  static const String addressKey = 'server_address';
  static const String tokenKey = 'api_token';

  final SecureStore _secure;

  /// `null` si no hay configuración, si falta una de las dos mitades o si lo
  /// guardado ya no pasa las reglas (por ejemplo, una dirección http escrita
  /// por una versión anterior): mejor volver a pedirla que usar algo inválido.
  Future<ServerSettings?> read() async {
    final String? address = await _secure.read(addressKey);
    final String? token = await _secure.read(tokenKey);
    if (address == null || token == null) {
      return null;
    }
    try {
      return ServerSettings(
        address: ServerAddress.parse(address),
        token: ApiToken(token),
      );
    } on InvalidServerAddress {
      return null;
    } on InvalidApiToken {
      return null;
    }
  }

  Future<void> save(ServerSettings settings) async {
    await _secure.write(addressKey, settings.address.origin);
    await _secure.write(tokenKey, settings.token.value);
  }

  Future<void> clear() async {
    await _secure.delete(addressKey);
    await _secure.delete(tokenKey);
  }
}

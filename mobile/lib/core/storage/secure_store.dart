import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Lo que la app guarda en el almacenamiento seguro del sistema: la dirección
/// del servidor, el token y la clave de la base local.
///
/// Una interfaz de tres métodos para que los tests no toquen Keystore ni
/// Keychain, y para que nada más en la app sepa qué paquete hay debajo.
abstract interface class SecureStore {
  Future<String?> read(String key);

  Future<void> write(String key, String value);

  Future<void> delete(String key);
}

/// Keystore en Android y Keychain en iOS, por `flutter_secure_storage`.
///
/// En iOS, `first_unlock_this_device`: el dato no viaja en las copias de
/// seguridad ni a otro teléfono (sin el `this_device` iría en el respaldo de
/// iCloud), y se puede leer con el teléfono bloqueado después del primer
/// desbloqueo, que es lo que necesitará la cola de pendientes para enviar en
/// segundo plano. En Android, las opciones por omisión de la versión 11:
/// AES-GCM con la llave envuelta en el Keystore.
class FlutterSecureStore implements SecureStore {
  const FlutterSecureStore();

  static const FlutterSecureStorage _storage = FlutterSecureStorage(
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
    ),
  );

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) =>
      _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}

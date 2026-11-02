import 'package:mobile/core/storage/secure_store.dart';

/// El almacenamiento seguro, en memoria: los tests no tocan Keystore ni
/// Keychain.
class FakeSecureStore implements SecureStore {
  final Map<String, String> values = <String, String>{};

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;

  @override
  Future<void> delete(String key) async => values.remove(key);
}

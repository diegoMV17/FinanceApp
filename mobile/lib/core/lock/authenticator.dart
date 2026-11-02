import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:local_auth/local_auth.dart';

/// Lo que puede salir de pedirle al sistema que desbloquee.
enum AuthOutcome {
  success,

  /// Fallido, cancelado, o el sistema no pudo mostrar el diálogo.
  failed,

  /// El teléfono no tiene ningún bloqueo de pantalla: no hay con qué pedirlo.
  noDeviceLock,
}

/// El desbloqueo del sistema, detrás de una interfaz: los tests no tienen ni
/// huella ni PIN.
abstract interface class Authenticator {
  Future<AuthOutcome> authenticate();
}

/// Huella, rostro o el PIN, patrón o contraseña del sistema (`local_auth` con
/// `biometricOnly: false`).
class LocalAuthAuthenticator implements Authenticator {
  LocalAuthAuthenticator([LocalAuthentication? auth])
    : _auth = auth ?? LocalAuthentication();

  final LocalAuthentication _auth;

  @override
  Future<AuthOutcome> authenticate() async {
    try {
      if (!await _auth.isDeviceSupported()) {
        return AuthOutcome.noDeviceLock;
      }
      final bool ok = await _auth.authenticate(
        localizedReason: 'Desbloquea FinanceApp',
        // Pedir el PIN puede sacar la app a segundo plano un momento: que el
        // diálogo sobreviva a eso en vez de cancelarse.
        persistAcrossBackgrounding: true,
      );
      return ok ? AuthOutcome.success : AuthOutcome.failed;
    } on LocalAuthException catch (failure) {
      return failure.code == LocalAuthExceptionCode.noCredentialsSet
          ? AuthOutcome.noDeviceLock
          : AuthOutcome.failed;
    }
  }
}

final Provider<Authenticator> authenticatorProvider = Provider<Authenticator>(
  (Ref ref) => LocalAuthAuthenticator(),
);

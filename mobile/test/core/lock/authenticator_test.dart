import 'package:flutter_test/flutter_test.dart';
import 'package:local_auth/local_auth.dart';
import 'package:mobile/core/lock/authenticator.dart';

/// El traductor entre `local_auth` y lo que la app sabe decir.
void main() {
  test(
    'el PIN del sistema también vale: no todo teléfono tiene huella',
    () async {
      final _RecordingAuth auth = _RecordingAuth();

      await LocalAuthAuthenticator(auth).authenticate();

      expect(auth.biometricOnly, isFalse);
    },
  );

  test('cancelar es un desbloqueo fallido', () async {
    final _RecordingAuth auth = _RecordingAuth()
      ..failure = const LocalAuthException(
        code: LocalAuthExceptionCode.userCanceled,
      );

    expect(
      await LocalAuthAuthenticator(auth).authenticate(),
      AuthOutcome.failed,
    );
  });

  test('sin bloqueo de pantalla no hay con qué pedirlo', () async {
    final _RecordingAuth auth = _RecordingAuth()
      ..failure = const LocalAuthException(
        code: LocalAuthExceptionCode.noCredentialsSet,
      );

    expect(
      await LocalAuthAuthenticator(auth).authenticate(),
      AuthOutcome.noDeviceLock,
    );
  });
}

class _RecordingAuth implements LocalAuthentication {
  bool? biometricOnly;
  LocalAuthException? failure;

  @override
  Future<bool> isDeviceSupported() async => true;

  @override
  Future<bool> authenticate({
    required String localizedReason,
    Iterable<Object?> authMessages = const <Object?>[],
    bool biometricOnly = false,
    bool sensitiveTransaction = true,
    bool persistAcrossBackgrounding = false,
  }) async {
    this.biometricOnly = biometricOnly;
    final LocalAuthException? thrown = failure;
    if (thrown != null) {
      throw thrown;
    }
    return true;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

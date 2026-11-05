import '../../../core/network/connection_check.dart';

/// Lo que se le dice al usuario de cada resultado de probar la conexión, sea
/// al configurarla o ya adentro.
extension ConnectionResultMessage on ConnectionResult {
  String? get message => switch (this) {
    ConnectionResult.connected => null,
    ConnectionResult.wrongToken =>
      'El servidor respondió, pero ese token no es el suyo.',
    ConnectionResult.tooManyAttempts =>
      'Demasiados intentos fallidos desde esta red. Espera un minuto y vuelve '
          'a intentarlo.',
    ConnectionResult.unreachable =>
      'No se pudo llegar al servidor. Revisa la dirección y que Tailscale '
          'esté conectado.',
    ConnectionResult.unexpected =>
      'En esa dirección respondió algo que no es la API de FinanceApp.',
  };
}

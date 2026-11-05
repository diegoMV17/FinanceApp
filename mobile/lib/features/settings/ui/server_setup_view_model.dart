import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/connection_check.dart';
import '../../../core/storage/server_settings.dart';
import '../../../core/storage/server_settings_store.dart';
import '../data/settings_repository.dart';
import 'connection_messages.dart';

/// Lo que muestra la pantalla de conexión. Nunca guarda lo que se escribió:
/// ni la dirección ni, sobre todo, el token.
class ServerSetupState {
  const ServerSetupState({
    this.addressError,
    this.tokenError,
    this.busy = false,
    this.connected = false,
    this.failure,
  });

  final String? addressError;
  final String? tokenError;
  final bool busy;
  final bool connected;
  final ConnectionResult? failure;

  String? get failureMessage => failure?.message;

  @override
  String toString() =>
      'ServerSetupState(busy: $busy, connected: $connected, '
      'failure: ${failure?.name})';
}

class ServerSetupViewModel extends Notifier<ServerSetupState> {
  @override
  ServerSetupState build() => const ServerSetupState();

  /// Valida el formato, prueba la conexión y, si respondió, guarda. Lo mal
  /// formado ni siquiera sale a la red.
  Future<void> submit(String addressText, String tokenText) async {
    if (state.busy) {
      return;
    }
    ServerAddress? address;
    ApiToken? token;
    String? addressError;
    String? tokenError;
    try {
      address = ServerAddress.parse(addressText);
    } on InvalidServerAddress catch (invalid) {
      addressError = _addressMessage(invalid.problem);
    }
    try {
      token = ApiToken(tokenText);
    } on InvalidApiToken catch (invalid) {
      tokenError = _tokenMessage(invalid.problem);
    }
    if (address == null || token == null) {
      state = ServerSetupState(
        addressError: addressError,
        tokenError: tokenError,
      );
      return;
    }

    state = const ServerSetupState(busy: true);
    final ConnectionResult result = await ref
        .read(settingsRepositoryProvider)
        .connect(ServerSettings(address: address, token: token));
    if (result == ConnectionResult.connected) {
      state = const ServerSetupState(connected: true);
      ref.invalidate(serverSettingsProvider);
    } else {
      state = ServerSetupState(failure: result);
    }
  }

  static String _addressMessage(ServerAddressProblem problem) =>
      switch (problem) {
        ServerAddressProblem.empty => 'Escribe la dirección del servidor.',
        ServerAddressProblem.malformed => 'Eso no es una dirección.',
        ServerAddressProblem.notHttps =>
          'Tiene que ser https: sin cifrado, el token viajaría '
              'legible.',
        ServerAddressProblem.noHost => 'Falta el nombre del servidor.',
        ServerAddressProblem.hasCredentials =>
          'Sin usuario ni contraseña en la dirección: el token va aparte.',
        ServerAddressProblem.hasPath ||
        ServerAddressProblem.hasQueryOrFragment =>
          'Solo la dirección del servidor, sin nada después del nombre.',
      };

  static String _tokenMessage(ApiTokenProblem problem) => switch (problem) {
    ApiTokenProblem.tooShort =>
      'Ese token es demasiado corto: el del servidor mide al menos '
          '${ApiToken.minLength} caracteres.',
    ApiTokenProblem.invalidCharacters =>
      'El token trae espacios, saltos de línea o caracteres que no son suyos.',
  };
}

final NotifierProvider<ServerSetupViewModel, ServerSetupState>
serverSetupViewModelProvider =
    NotifierProvider<ServerSetupViewModel, ServerSetupState>(
      ServerSetupViewModel.new,
    );

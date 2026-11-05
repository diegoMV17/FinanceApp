import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'server_setup_view_model.dart';

/// La primera pantalla: la dirección del servidor y el token, a mano.
///
/// Los dos son privados y no van en el código: la dirección es el nombre de
/// la tailnet y el token abre el libro entero. El campo del token va oculto,
/// sin sugerencias ni aprendizaje del teclado, y con el texto oculto el
/// sistema no ofrece copiarlo ni cortarlo; pegarlo sí se puede.
class ServerSetupScreen extends ConsumerStatefulWidget {
  const ServerSetupScreen({super.key});

  static const Key addressFieldKey = Key('server-address');
  static const Key tokenFieldKey = Key('api-token');
  static const Key connectButtonKey = Key('connect');

  @override
  ConsumerState<ServerSetupScreen> createState() => _ServerSetupScreenState();
}

class _ServerSetupScreenState extends ConsumerState<ServerSetupScreen> {
  final TextEditingController _address = TextEditingController();
  final TextEditingController _token = TextEditingController();

  @override
  void dispose() {
    _address.dispose();
    // El texto del token no se queda en memoria más de lo necesario.
    _token
      ..clear()
      ..dispose();
    super.dispose();
  }

  Future<void> _connect() async {
    await ref
        .read(serverSetupViewModelProvider.notifier)
        .submit(_address.text, _token.text);
  }

  @override
  Widget build(BuildContext context) {
    final ServerSetupState state = ref.watch(serverSetupViewModelProvider);
    final String? failure = state.failureMessage;
    return Scaffold(
      appBar: AppBar(title: const Text('Conectar con el servidor')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: <Widget>[
            const Text(
              'La dirección y el token se escriben aquí una sola vez y se '
              'guardan en el almacenamiento seguro del teléfono.',
            ),
            const SizedBox(height: 24),
            TextField(
              key: ServerSetupScreen.addressFieldKey,
              controller: _address,
              enabled: !state.busy,
              keyboardType: TextInputType.url,
              autocorrect: false,
              enableSuggestions: false,
              decoration: InputDecoration(
                labelText: 'Dirección del servidor',
                helperText: 'La de Tailscale, con https',
                errorText: state.addressError,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              key: ServerSetupScreen.tokenFieldKey,
              controller: _token,
              enabled: !state.busy,
              obscureText: true,
              autocorrect: false,
              enableSuggestions: false,
              enableIMEPersonalizedLearning: false,
              keyboardType: TextInputType.visiblePassword,
              decoration: InputDecoration(
                labelText: 'Token',
                errorText: state.tokenError,
              ),
            ),
            const SizedBox(height: 24),
            if (failure != null) ...<Widget>[
              Text(
                failure,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
              const SizedBox(height: 16),
            ],
            FilledButton(
              key: ServerSetupScreen.connectButtonKey,
              onPressed: state.busy ? null : _connect,
              child: state.busy
                  ? const SizedBox.square(
                      dimension: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Probar y guardar'),
            ),
          ],
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';

/// Tapa la app entera mientras no está al frente.
///
/// Es para iOS: la foto que el sistema guarda para el selector de apps se toma
/// con la app en `inactive`, y sin esto quedarían a la vista los saldos de la
/// última pantalla. En Android lo hace `FLAG_SECURE` desde `MainActivity`, que
/// además impide las capturas de pantalla.
class PrivacyCover extends StatefulWidget {
  const PrivacyCover({required this.child, super.key});

  static const Key coverKey = Key('privacy-cover');

  final Widget child;

  @override
  State<PrivacyCover> createState() => _PrivacyCoverState();
}

class _PrivacyCoverState extends State<PrivacyCover>
    with WidgetsBindingObserver {
  bool _covered = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final bool covered = state != AppLifecycleState.resumed;
    if (covered != _covered) {
      setState(() => _covered = covered);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_covered) {
      return widget.child;
    }
    // La cubierta reemplaza al contenido en vez de ponerse encima: así no hay
    // un cuadro en el que el contenido se alcance a ver debajo.
    return ColoredBox(
      key: PrivacyCover.coverKey,
      color: Theme.of(context).colorScheme.surface,
      child: const Center(child: Icon(Icons.lock_outline, size: 48)),
    );
  }
}

import 'package:flutter/material.dart';

/// Arranque, tema y rutas de la app.
///
/// Es un cliente: todo lo que se muestre aquí lo decide el servidor. Mientras
/// no haya contrato ni cliente generado, la única pantalla dice qué es la app
/// y no pinta ninguna cifra.
class FinanceApp extends StatelessWidget {
  const FinanceApp({super.key});

  static const Color _seed = Color(0xFF1B6B50);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FinanceApp',
      theme: ThemeData(colorSchemeSeed: _seed, brightness: Brightness.light),
      darkTheme: ThemeData(colorSchemeSeed: _seed, brightness: Brightness.dark),
      home: const _UnderConstructionScreen(),
    );
  }
}

class _UnderConstructionScreen extends StatelessWidget {
  const _UnderConstructionScreen();

  @override
  Widget build(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                Text('FinanceApp', style: text.headlineMedium),
                const SizedBox(height: 8),
                Text(
                  'El esqueleto de la app. Las pantallas llegan con el '
                  'contrato y el cliente generado.',
                  style: text.bodyMedium,
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Un contador que sube cada vez que el libro cambió desde este teléfono: un
/// movimiento de la cola que el servidor aceptó, una cuenta, una persona o un
/// presupuesto escritos en línea.
///
/// Las lecturas lo miran y se vuelven a pedir: así el tablero se refresca
/// cuando un gasto **llegó** al servidor, no cuando se encoló (encolado
/// todavía no cuenta en ninguna cifra). Vive en `core/` porque lo suben
/// `data/sync` y varias funcionalidades, y lo leen otras.
class BookChanges extends Notifier<int> {
  @override
  int build() => 0;

  void changed() => state++;
}

final NotifierProvider<BookChanges, int> bookChangesProvider =
    NotifierProvider<BookChanges, int>(BookChanges.new);
